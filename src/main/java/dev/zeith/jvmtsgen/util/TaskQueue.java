package dev.zeith.jvmtsgen.util;

import lombok.SneakyThrows;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Semaphore;

public class TaskQueue
		implements Runnable, AutoCloseable
{
	protected final Object lock = new Object();
	protected final Queue<Runnable> tasks = new ArrayDeque<>();
	protected final Semaphore closureSemaphore = new Semaphore(0);
	
	protected boolean running = false;
	
	protected int activeTasks = 0;
	
	@Override
	public void run()
	{
		while(true)
		{
			Runnable task;
			
			synchronized(lock)
			{
				while(running && tasks.isEmpty())
				{
					try
					{
						lock.wait();
					} catch(InterruptedException e)
					{
						Thread.currentThread().interrupt();
						closureSemaphore.release();
						return;
					}
				}
				
				if(!running && tasks.isEmpty())
				{
					closureSemaphore.release();
					lock.notifyAll();
					return;
				}
				
				task = tasks.poll();
				activeTasks++;
			}
			
			try
			{
				if(task != null)
					task.run();
			} catch(Throwable t)
			{
				if(t instanceof InterruptedException)
				{
					Thread.currentThread().interrupt();
					closureSemaphore.release();
					return;
				}
				
				// Prevent worker from dying
				t.printStackTrace();
			} finally
			{
				synchronized(lock)
				{
					activeTasks--;
					lock.notifyAll();
				}
			}
		}
	}
	
	public void defer(Runnable task)
	{
		if(task == null) return;
		
		synchronized(lock)
		{
			if(!running)
				throw new IllegalStateException("TaskQueue is stopped");
			
			tasks.add(task);
			lock.notifyAll();
		}
	}
	
	public void stop()
	{
		synchronized(lock)
		{
			running = false;
			lock.notifyAll();
		}
	}
	
	public void waitForIdle()
			throws InterruptedException
	{
		synchronized(lock)
		{
			while(!tasks.isEmpty() || activeTasks > 0)
			{
				lock.wait();
			}
		}
	}
	
	public void waitFor()
			throws InterruptedException
	{
		closureSemaphore.acquire();
	}
	
	public static void waitForIdle(Iterable<TaskQueue> queues)
	{
		for(TaskQueue v : queues)
		{
			try
			{
				v.waitForIdle();
			} catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
	
	public static void closeAll(Iterable<TaskQueue> queues)
	{
		for(TaskQueue v : queues) v.stop();
		
		for(TaskQueue v : queues)
		{
			try
			{
				v.waitFor();
			} catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
	
	@Override
	@SneakyThrows
	public void close()
	{
		stop();
		waitFor();
	}
	
	public static TaskQueue createStarted()
	{
		TaskQueue queue = new TaskQueue();
		queue.running = true;
		async("TaskQueue", queue);
		return queue;
	}
	
	public static void async(String name, Runnable task)
	{
		Thread thread = tryStartVirtual(task);
		if(thread == null)
		{
			thread = new Thread(task, name);
			thread.start();
		}
	}
	
	private static Thread tryStartVirtual(Runnable task)
	{
		try
		{
			Method ofVirtual = Thread.class.getMethod("ofVirtual");
			Object builder = ofVirtual.invoke(null);
			
			Method start = builder.getClass().getMethod("start", Runnable.class);
			return (Thread) start.invoke(builder, task);
		} catch(Throwable ignored)
		{
			return null;
		}
	}
}