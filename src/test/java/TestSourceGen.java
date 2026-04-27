import dev.zeith.jvmtsgen.src.Source;
import dev.zeith.jvmtsgen.tasks.ImportStrategy;
import dev.zeith.jvmtsgen.util.TsMultiGenerator;
import dev.zeith.tsgen.*;
import dev.zeith.tsgen.api.IGenerationExtension;
import dev.zeith.tsgen.imports.BaseImportModel;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.*;

public class TestSourceGen
{
	public static void main(String[] args)
			throws IOException
	{
		File genTs = new File("build/generated/ts");
		deleteDir(genTs);
		
		List<Source> allSources = new ArrayList<>();
		
		allSources.add(Source.ofFile(
				new File("build/classes/java/main"),
				new File("src/main/java")
		));
		
//		allSources.add(Source.ofJrt().filter(s -> s.startsWith("java/") || s.startsWith("javax/")));
		
		var pathResolver = IPathResolver.FROM_PACKAGE;
		
		BaseImportModel importModel = ImportStrategy.IMPORT_FROM.getImportModel().clone();
		importModel.setFilePath(pathResolver);
		boolean detailedErrorLog = true;
		boolean logSkippedClasses = true;
		boolean noTsCheck = true;
		
		Supplier<BulkTypeScriptExporter> exporterFactory = () -> BulkTypeScriptExporter
				.builder()
				.outDir(genTs)
				.importModel(importModel)
				.pathResolver(pathResolver)
				.configurator(tsg -> tsg.withExceptionHandler(GeneratorExceptionHandler.SKIP_FAILED_ENTRY))
				.build();
		
		Predicate<String> filter = f -> f != null;
		
		TsMultiGenerator.generate(
				allSources,
				exporterFactory,
				filter,
				importModel,
				logSkippedClasses,
				detailedErrorLog,
				noTsCheck,
				64,
				IGenerationExtension.DEFAULT_ENABLED
		);
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