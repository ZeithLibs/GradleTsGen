package dev.zeith.jvmtsgen.tasks;

import dev.zeith.jvmtsgen.src.Source;
import dev.zeith.jvmtsgen.util.*;
import dev.zeith.tsgen.*;
import dev.zeith.tsgen.imports.BaseImportModel;
import groovy.lang.Closure;
import lombok.Setter;
import org.gradle.api.*;
import org.gradle.api.artifacts.*;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.*;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.work.DisableCachingByDefault;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.*;

@DisableCachingByDefault(because = "Typescript generation is an optional task and should always run when called.")
public abstract class GenTypescriptTask
		extends DefaultTask
{
	private final GeneratorSettings generatorSettings = new GeneratorSettings();
	
	@Setter
	private Spec<String> classFilter = s -> true;
	
	@OutputDirectory
	public abstract DirectoryProperty getOutputDir();
	
	@Input
	@Optional
	public abstract Property<Boolean> getIncludeJvm();
	
	@Input
	@Optional
	public abstract Property<Boolean> getCleanOutputDir();
	
	@Input
	@Optional
	public abstract Property<Integer> getMaxQueueSize();
	
	@Input
	@Optional
	public abstract ListProperty<String> getEnabledExtensions();
	
	@Input
	@Optional
	public abstract ListProperty<String> getDisabledExtensions();
	
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
		
		BaseImportModel importModel = generatorSettings.getImportStrategy().getImportModel().clone();
		importModel.setFilePath(pathResolver);
		boolean detailedErrorLog = generatorSettings.isDetailedErrorLog();
		boolean logSkippedClasses = generatorSettings.isLogSkippedClasses();
		
		Supplier<BulkTypeScriptExporter> exporterFactory = () -> BulkTypeScriptExporter
				.builder()
				.outDir(outDir)
				.importModel(importModel)
				.pathResolver(pathResolver)
				.configurator(tsg ->
				{
					generatorSettings.apply(tsg);
					tsg.exceptionHandler(GeneratorExceptionHandler.SKIP_FAILED_ENTRY);
				})
				.build();
		
		Predicate<String> filter = classFilter::isSatisfiedBy;
		
		var enabledExt = Set.copyOf(getEnabledExtensions().getOrElse(List.of()));
		var disabledExt = Set.copyOf(getDisabledExtensions().getOrElse(List.of()));
		
		TsMultiGenerator.generate(
				allSources,
				exporterFactory,
				filter,
				importModel,
				logSkippedClasses,
				detailedErrorLog,
				generatorSettings.isNoTsCheck(),
				getMaxQueueSize().getOrElse(0),
				ext ->
				{
					var id = ext.getId();
					return enabledExt.contains(id) || (ext.defaultEnabled() && !disabledExt.contains(id));
				},
				generatorSettings.getNewline()
		);
	}
	
	public void generatorSettings(Action<? super GeneratorSettings> action)
	{
		action.execute(generatorSettings);
	}
	
	public void generatorSettings(Closure<?> closure)
	{
		ConfigureUtil.configure(closure, generatorSettings);
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