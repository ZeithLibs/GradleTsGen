package dev.zeith.jvmtsgen.util;

import dev.zeith.jvmtsgen.src.Source;
import dev.zeith.tsgen.BulkTypeScriptExporter;
import dev.zeith.tsgen.imports.BaseImportModel;
import dev.zeith.tsgen.parse.model.ClassModel;
import dev.zeith.tsgen.parse.src.model.SourceClassModel;
import dev.zeith.tsgen.parse.src.parse.ISourceParserFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class TsMultiGenerator
{
	public static void generate(
			List<Source> allSources,
			Supplier<BulkTypeScriptExporter> exporterFactory,
			Predicate<String> filter,
			BaseImportModel importModel,
			boolean logSkippedClasses, boolean detailedErrorLog,
			boolean tsNoCheck,
			int maxQueueSize
	)
			throws IOException
	{
		String prefix = tsNoCheck ? "// @ts-nocheck\n" : "";
		
		
		Map<String, TaskQueue> saveTasks = new LinkedHashMap<>();
		
		final IntSupplier threadCount;
		final Function<String, TaskQueue> compute;
		
		if(maxQueueSize > 0)
		{
			List<TaskQueue> queueCache = new ArrayList<>();
			for(int i = 0; i < maxQueueSize; i++) queueCache.add(TaskQueue.createStarted());
			AtomicInteger queuePointer = new AtomicInteger(0);
			IntUnaryOperator updator = i -> (i + 1) % maxQueueSize;
			IntSupplier nextQueue = () -> queuePointer.getAndUpdate(updator);
			compute = n -> queueCache.get(nextQueue.getAsInt());
			threadCount = queueCache::size;
		} else
		{
			AtomicInteger threadCounter = new AtomicInteger(0);
			compute = n ->
			{
				threadCounter.incrementAndGet();
				return TaskQueue.createStarted();
			};
			threadCount = threadCounter::get;
		}
		
		Function<String, TaskQueue> getQueue = n -> saveTasks.computeIfAbsent(n, compute);
		
		Map<String, Runnable> optimizeTasks = new ConcurrentHashMap<>();
		AtomicInteger taskCount = new AtomicInteger();
		
		Map<String, SourceClassModel> emptyMap = Map.of();
		
		for(Source src : allSources)
		{
			var exporter = exporterFactory.get();
			
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
				
				TaskQueue queue = getQueue.apply(queueName);
				
				taskCount.incrementAndGet();
				queue.defer(() ->
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
						File fl = exporter.export(model, classes.get(model.getSimpleName()));
						
						optimizeTasks.put(queueName, () ->
								{
									try
									{
										BulkTypeScriptExporter.optimize(fl, importModel, prefix, "");
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
		
		System.out.println("Waiting for " + taskCount + " tasks in " + saveTasks.values().size() + " files to complete in " + threadCount.getAsInt() + " threads");
		TaskQueue.waitForIdle(saveTasks.values());
		
		System.out.println("Initializing import optimization...");
		for(Map.Entry<String, Runnable> e : optimizeTasks.entrySet())
			getQueue.apply(e.getKey()).defer(e.getValue());
		
		System.out.println("Waiting for optimization to complete...");
		TaskQueue.closeAll(saveTasks.values());
		
		System.out.println("TS generation complete.");
	}
}
