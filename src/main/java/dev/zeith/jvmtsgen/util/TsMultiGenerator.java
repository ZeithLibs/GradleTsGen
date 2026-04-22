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
		
		Map<String, TaskQueue> saveTasks = new ConcurrentHashMap<>();
		
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
		AtomicInteger taskCount = new AtomicInteger(0);
		AtomicInteger activeTaskCount = new AtomicInteger(0);
		final Object onTaskZero = new Object();
		
		Map<String, SourceClassModel> emptyMap = Map.of();
		
		System.out.println("[TsMultiGenerator] Loading " + allSources.size() + " sources...");
		List<Thread> visitThreads = new ArrayList<>();
		for(Source src : allSources)
		{
			var exporter = exporterFactory.get();
			
			var t = TaskQueue.async("TsMultiGenerator-visit-" + src.getName(), () ->
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
								
								TaskQueue queue = getQueue.apply(queueName);
								
								taskCount.incrementAndGet();
								activeTaskCount.incrementAndGet();
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
									} finally
									{
										int v = activeTaskCount.decrementAndGet();
										if(v == 0) synchronized(onTaskZero)
										{
											onTaskZero.notifyAll();
										}
									}
								});
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
		
		System.out.println("[TsMultiGenerator] Waiting for " + taskCount + " tasks in " + saveTasks.values().size() + " files to complete in " + threadCount.getAsInt() + " threads");
		while(activeTaskCount.get() > 0)
		{
			try
			{
				synchronized(onTaskZero)
				{
					onTaskZero.wait(1000L);
				}
			} catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
			int v = activeTaskCount.get();
			if(v > 0) System.out.println("[TsMultiGenerator] Waiting for " + v + " more tasks to complete...");
		}
		TaskQueue.waitForIdle(saveTasks.values());
		
		System.out.println("[TsMultiGenerator] Initializing import optimization...");
		for(Map.Entry<String, Runnable> e : optimizeTasks.entrySet())
			getQueue.apply(e.getKey()).defer(e.getValue());
		
		System.out.println("[TsMultiGenerator] Waiting for optimization to complete...");
		TaskQueue.closeAll(saveTasks.values());
		
		System.out.println("[TsMultiGenerator] TS generation complete.");
	}
}
