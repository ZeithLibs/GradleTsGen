package dev.zeith.jvmtsgen.util;

import lombok.*;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class FileLockedExecutorWrapper
		implements AutoCloseable
{
	protected final Executor delegate;
	protected final boolean closeDelegate;
	protected final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
	protected final Function<String, Object> lockFactory = s -> new Object();
	protected final @Getter AtomicInteger taskCount = new AtomicInteger(0);
	
	public FileLockedExecutorWrapper(Executor delegate, boolean closeDelegate)
	{
		this.delegate = delegate;
		this.closeDelegate = closeDelegate;
	}
	
	public void execute(String path, Runnable task)
	{
		var lock = locks.computeIfAbsent(path, lockFactory);
		taskCount.incrementAndGet();
		delegate.execute(() ->
		{
			try
			{
				synchronized(lock)
				{
					task.run();
				}
			} finally
			{
				if(taskCount.decrementAndGet() == 0) synchronized(this)
				{
					this.notifyAll();
				}
			}
		});
	}
	
	public Set<String> getPaths()
	{
		return locks.keySet();
	}
	
	@Override
	@SneakyThrows
	public void close()
	{
		locks.clear();
		if(closeDelegate)
		{
			if(delegate instanceof AutoCloseable ac)
				ac.close();
			else if(delegate instanceof ExecutorService es)
			{
				es.shutdown();
				es.awaitTermination(60L, TimeUnit.SECONDS);
			}
		}
	}
}