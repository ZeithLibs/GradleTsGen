package dev.zeith.jvmtsgen.tasks;

import dev.zeith.jvmtsgen.src.Source;
import dev.zeith.jvmtsgen.util.TsMultiGenerator;
import dev.zeith.tsgen.*;
import dev.zeith.tsgen.imports.BaseImportModel;
import lombok.Setter;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.*;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.*;

@DisableCachingByDefault(because = "Typescript generation is an optional task and should always run when called.")
public abstract class GenTypescriptTask
		extends DefaultTask
{
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
	
	@Input
	@Optional
	public abstract Property<Boolean> getTsNoCheck();
	
	@Input
	@Optional
	public abstract Property<Integer> getMaxQueueSize();
	
	@Setter
	private Spec<String> classFilter = s -> true;
	
	@TaskAction
	public void doTask()
			throws IOException
	{
		final String sourceSetName = "main";
		
		File outDir = getOutputDir().get().getAsFile();
		if(outDir.exists() && getCleanOutputDir().getOrElse(true)) deleteDir(outDir);
		outDir.mkdirs();
		
		List<Source> allSources = new ArrayList<>();
		
		//<editor-fold desc="Source resolution">
		Map<String, String> artifactKeys = new HashMap<>();
		Map<String, File> sourceMap = new HashMap<>();
		ResolvedConfiguration resolved = getProject().getConfigurations().getByName("compileClasspath").getResolvedConfiguration();
		for(ResolvedArtifact artifact : resolved.getResolvedArtifacts())
		{
			ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
			String key = id.getGroup() + ":" + id.getName() + ":" + id.getVersion();
			Configuration sourceCfg = getProject().getConfigurations().detachedConfiguration();
			
			sourceCfg.setTransitive(false);
			
			Dependency dep = getProject().getDependencies().create(key + ":sources");
			
			sourceCfg.getDependencies().add(dep);
			
			File srcJar = null;
			try
			{
				srcJar = sourceCfg.resolve().stream().findFirst().orElse(null);
			} catch(Exception ignored) {}
			
			artifactKeys.put(artifact.getFile().getAbsolutePath(), key);
			sourceMap.put(key, srcJar);
		}
		
		SourceSet srcSet = getProject()
				.getExtensions()
				.getByType(SourceSetContainer.class)
				.getByName(sourceSetName);
		
		Set<File> unifiedClasspath = new LinkedHashSet<>();
		unifiedClasspath.addAll(srcSet.getCompileClasspath().getFiles());
		unifiedClasspath.addAll(srcSet.getOutput().getClassesDirs().getFiles());
		for(File file : unifiedClasspath)
		{
			String key = artifactKeys.getOrDefault(file.getAbsolutePath(), file.getName());
			File srcFile = sourceMap.get(key);
			
			if(srcFile == null && isProjectOutput(file, srcSet))
				srcFile = srcSet
						.getAllSource()
						.getSrcDirs()
						.stream()
						.filter(f -> file.getAbsolutePath().replace(File.separator, "/").endsWith(f.getName() + "/" + sourceSetName))
						.filter(File::isDirectory)
						.findFirst()
						.orElse(null);
			
			if(srcFile != null)
				System.out.println("Add source to " + key + " - " + srcFile.getAbsolutePath());
			
			allSources.add(Source.ofFile(file, srcFile));
		}
		//</editor-fold>
		
		if(getIncludeJvm().getOrElse(true))
			allSources.add(Source.ofJrt().filter(s -> s.startsWith("java/") || s.startsWith("javax/")));
		
		var pathResolver = IPathResolver.FROM_PACKAGE;
		
		BaseImportModel importModel = getImportStrategy().getOrElse(ImportStrategy.IMPORT_FROM).getImportModel().clone();
		importModel.setFilePath(pathResolver);
		boolean detailedErrorLog = getDetailedErrorLog().getOrElse(false);
		boolean logSkippedClasses = getLogSkippedClasses().getOrElse(false);
		
		Supplier<BulkTypeScriptExporter> exporterFactory = () -> BulkTypeScriptExporter
				.builder()
				.outDir(outDir)
				.importModel(importModel)
				.pathResolver(pathResolver)
				.configurator(tsg -> tsg.withExceptionHandler(GeneratorExceptionHandler.SKIP_FAILED_ENTRY))
				.build();
		
		Predicate<String> filter = classFilter::isSatisfiedBy;
		
		TsMultiGenerator.generate(
				allSources,
				exporterFactory,
				filter,
				importModel,
				logSkippedClasses,
				detailedErrorLog,
				getTsNoCheck().getOrElse(true),
				getMaxQueueSize().getOrElse(0)
		);
	}
	
	private boolean isProjectOutput(File file, SourceSet main)
	{
		return main.getOutput().getClassesDirs().getFiles().contains(file)
				|| file.getAbsolutePath().contains("build/classes");
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