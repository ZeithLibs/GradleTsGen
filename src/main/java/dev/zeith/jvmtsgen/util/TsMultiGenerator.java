package dev.zeith.jvmtsgen.util;

import dev.zeith.jvmtsgen.src.Source;
import dev.zeith.tsgen.BulkTypeScriptExporter;
import dev.zeith.tsgen.api.IGenerationExtension;
import dev.zeith.tsgen.imports.BaseImportModel;
import dev.zeith.tsgen.parse.model.ClassModel;
import dev.zeith.tsgen.parse.src.model.SourceClassModel;
import dev.zeith.tsgen.parse.src.parse.ISourceParserFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import static dev.zeith.jvmtsgen.util.ThreadInitializer.async;

public class TsMultiGenerator
{
	public static void generate(
			List<Source> allSources,
			Supplier<BulkTypeScriptExporter> exporterFactory,
			Predicate<String> filter,
			BaseImportModel importModel,
			boolean logSkippedClasses, boolean detailedErrorLog,
			boolean tsNoCheck,
			int maxQueueSize,
			Predicate<IGenerationExtension> enabledExtensions,
			String newline
	)
	{
		String prefix = tsNoCheck ? "// @ts-nocheck\n" : "";
		
		Supplier<ExecutorService> exec = () -> maxQueueSize == 0 ? Executors.newWorkStealingPool() : Executors.newWorkStealingPool(maxQueueSize);
		
		Map<String, Runnable> optimizeTasks = new ConcurrentHashMap<>();
		AtomicInteger taskCount = new AtomicInteger(0);
		
		Map<String, SourceClassModel> emptyMap = Map.of();
		
		FileLockedExecutorWrapper genPool = new FileLockedExecutorWrapper(exec.get(), true);
		System.out.println("[TsMultiGenerator] Loading " + allSources.size() + " sources...");
		List<Thread> visitThreads = new ArrayList<>();
		for(Source src : allSources)
		{
			var exporter = exporterFactory.get();
			
			var t = async("TsMultiGenerator-visit-" + src.getName(), () ->
					{
						try
						{
							src.visit((name, buffer, sourceCode) ->
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
								
								if(!filter.test(internalName)) return;
								
								taskCount.incrementAndGet();
								genPool.execute(queueName, () ->
										{
											try
											{
												ClassModel model = ClassModel.parse(buffer);
												if(model == null || !model.name().getInternalName().contains("/") || !model.isPublic()) return;
												
												Map<String, SourceClassModel> classes = sourceCode.map(code ->
												{
													try
													{
														var srcParser = ISourceParserFactory.BLEEDING_EDGE.createParser();
														return SourceClassModel.parse(srcParser, code);
													} catch(Throwable e)
													{
														return emptyMap;
													}
												}).orElse(emptyMap);
												
												// If you're exporting to different files, this may be async. (Manual async implementation required)
												File fl = exporter.export(model, classes.get(model.getSimpleName()), enabledExtensions);
												
												optimizeTasks.put(queueName, () ->
														{
															try
															{
																BulkTypeScriptExporter.optimize(fl, importModel, newline, prefix, "");
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
										}
								);
							});
						} catch(IOException e)
						{
							System.err.println("[TsMultiGenerator] Failed to visit source " + src.getName());
							e.printStackTrace();
						}
					}
			);
			
			visitThreads.add(t);
		}
		
		for(Thread t : visitThreads)
		{
			try
			{
				t.join();
			} catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}
		
		System.out.println("[TsMultiGenerator] Waiting for " + taskCount + " tasks in " + genPool.getPaths().size() + " files to complete.");
		AtomicInteger activeTaskCount = genPool.getTaskCount();
		if(awaitAndClose(genPool, activeTaskCount, genPool)) return;
		
		FileLockedExecutorWrapper optPool = new FileLockedExecutorWrapper(exec.get(), true);
		
		System.out.println("[TsMultiGenerator] Initializing import optimization...");
		for(Map.Entry<String, Runnable> e : optimizeTasks.entrySet())
			optPool.execute(e.getKey(), e.getValue());
		
		System.out.println("[TsMultiGenerator] Waiting for optimization to complete...");
		activeTaskCount = optPool.getTaskCount();
		if(awaitAndClose(genPool, activeTaskCount, optPool)) return;
		
		System.out.println("[TsMultiGenerator] TS generation complete.");
	}
	
	private static boolean awaitAndClose(FileLockedExecutorWrapper genPool, AtomicInteger activeTaskCount, FileLockedExecutorWrapper optPool)
	{
		while(activeTaskCount.get() > 0)
		{
			try
			{
				synchronized(genPool)
				{
					genPool.wait(1000L);
				}
			} catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return true;
			}
			int v = activeTaskCount.get();
			if(v > 0) System.out.println("[TsMultiGenerator] Waiting for " + v + " more tasks to complete...");
		}
		optPool.close();
		return false;
	}
}
