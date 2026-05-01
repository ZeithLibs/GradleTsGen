package dev.zeith.jvmtsgen.util;

import java.lang.reflect.Method;

public class ThreadInitializer
{
	public static Thread async(String name, Runnable task)
	{
		Thread thread = tryStartVirtual(task);
		if(thread == null)
		{
			thread = new Thread(task, name);
			thread.start();
		}
		return thread;
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