package dev.zeith.jvmtsgen.util;

import java.util.*;
import java.util.function.*;

public class LazyOptional<T>
{
	private static final LazyOptional EMPTY = new LazyOptional<>(null);
	protected Supplier<T> factory;
	protected T value;
	
	LazyOptional(Supplier<T> factory)
	{
		this.factory = factory;
	}
	
	public static <T> LazyOptional<T> empty()
	{
		return EMPTY;
	}
	
	public static <T> LazyOptional<T> of(Supplier<T> factory)
	{
		return new LazyOptional<>(factory);
	}
	
	public boolean isPresent()
	{
		return factory != null;
	}
	
	public T get()
	{
		synchronized(this)
		{
			if(factory != null)
			{
				value = factory.get();
			}
		}
		return value;
	}
	
	public <R> LazyOptional<R> map(Function<T, R> o)
	{
		return isPresent() ? of(() -> o.apply(get())) : empty();
	}
	
	public T orElse(T of)
	{
		return isPresent() ? get() : of;
	}
}