package dev.zeith.jvmtsgen.tasks;

import dev.zeith.jvmtsgen.src.Source;
import dev.zeith.jvmtsgen.util.TaskQueue;
import dev.zeith.tsgen.*;
import dev.zeith.tsgen.imports.BaseImportModel;
import dev.zeith.tsgen.parse.ClassModel;
import lombok.Setter;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.*;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

@DisableCachingByDefault(because = "Typescript generation is an optional task and should always run when called.")
public abstract class GenTypescriptTask
		extends DefaultTask
{
	@InputFiles
	public abstract ConfigurableFileCollection getClasspath();
	
	@OutputDirectory
	public abstract DirectoryProperty getOutputDir();
	
	@Input
	@Optional
	public abstract Property<ImportStrategy> getImportStrategy();
	
	@Input
	@Optional
	public abstract Property<Boolean> getLogSkippedClasses();
	
	@Input
	@Optional
	public abstract Property<Boolean> getDetailedErrorLog();
	
	@Input
	@Optional
	public abstract Property<Boolean> getIncludeJvm();
	
	@Input
	@Optional
	public abstract Property<Boolean> getCleanOutputDir();
	
	@Setter
	private Spec<String> classFilter = s -> true;
	
	@TaskAction
	public void doTask()
			throws IOException
	{
		File outDir = getOutputDir().get().getAsFile();
		if(outDir.exists() && getCleanOutputDir().getOrElse(true)) deleteDir(outDir);
		outDir.mkdirs();
		
		Spec<String> filter = classFilter;
		
		boolean detailedErrorLog = getDetailedErrorLog().getOrElse(false);
		
		Map<String, TaskQueue> saveTasks = new LinkedHashMap<>();
		Function<String, TaskQueue> compute = n -> TaskQueue.createStarted();
		Function<String, TaskQueue> getQueue = n -> saveTasks.computeIfAbsent(n, compute);
		
		var pathResolver = IPathResolver.FROM_PACKAGE;
		
		BaseImportModel importModel = getImportStrategy().getOrElse(ImportStrategy.REQUIRE).getImportModel().clone();
		importModel.setFilePath(pathResolver);
		boolean logSkippedClasses = getLogSkippedClasses().getOrElse(false);
		
		Supplier<BulkTypeScriptExporter> exporterFactory = () -> BulkTypeScriptExporter
				.builder()
				.outDir(outDir)
				.importModel(importModel)
				.pathResolver(pathResolver)
				.configurator(tsg -> tsg.withExceptionHandler(GeneratorExceptionHandler.SKIP_FAILED_ENTRY))
				.build();
		
		Map<String, Runnable> optimizeTasks = new ConcurrentHashMap<>();
		AtomicInteger taskCount = new AtomicInteger();
		
		List<Source> sources = new ArrayList<>();
		
		for(File file : getClasspath().getFiles())
			sources.add(Source.ofFile(file));
		
		if(getIncludeJvm().getOrElse(true))
			sources.add(Source.ofJrt().filter(s -> s.startsWith("java/") || s.startsWith("javax/")));
		
		for(Source src : sources)
		{
			var exporter = exporterFactory.get();
			
			src.visit((name, buffer) ->
			{
				if(name.equals("module-info.class")) return;
				
				String queueName;
				String internalName = name.substring(0, name.length() - 6); // remove .class
				try
				{
					queueName = exporter.getFilePathOf(internalName);
				} catch(StringIndexOutOfBoundsException e)
				{
					return;
				}
				
				if(!filter.isSatisfiedBy(internalName)) return;
				
				TaskQueue queue = getQueue.apply(queueName);
				
				taskCount.incrementAndGet();
				queue.defer(() ->
				{
					try
					{
						ClassModel model = ClassModel.parse(buffer);
						if(model == null || !model.name().getInternalName().contains("/") || !model.isPublic()) return;
						// If you're exporting to different files, this may be async. (Manual async implementation required)
						File fl = exporter.export(model);
						
						optimizeTasks.put(queueName, () ->
								{
									try
									{
										BulkTypeScriptExporter.optimize(fl, importModel);
									} catch(IOException e)
									{
										throw new UncheckedIOException(e);
									}
								}
						);
					} catch(Exception e)
					{
						if(logSkippedClasses)
						{
							System.err.println("[" + src.getName() + "] Failed to parse class @ " + name);
							if(detailedErrorLog) e.printStackTrace();
						}
					}
				});
			});
		}
		
		System.out.println("Waiting for " + taskCount + " tasks in " + saveTasks.values().size() + " files to complete");
		TaskQueue.waitForIdle(saveTasks.values());
		
		System.out.println("Initializing import optimization...");
		for(Map.Entry<String, Runnable> e : optimizeTasks.entrySet())
			getQueue.apply(e.getKey()).defer(e.getValue());
		
		System.out.println("Waiting for optimization to complete...");
		TaskQueue.closeAll(saveTasks.values());
		
		System.out.println("TS generation complete.");
	}
	
	public static void deleteDir(File dir)
			throws IOException
	{
		if(!dir.exists()) return;
		Files.walk(dir.toPath())
			 .sorted(Comparator.reverseOrder())
			 .forEach(p -> p.toFile().delete());
	}
}