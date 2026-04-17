package dev.zeith.jvmtsgen.src;

import dev.zeith.tsgen.parse.IClassFileVisitor;

import java.io.*;
import java.util.function.*;

public interface Source
{
	String getName();
	
	void visit(BiConsumer<String, byte[]> consumer)
			throws IOException;
	
	default Source filter(Predicate<String> filter)
	{
		Source base = this;
		return new Source()
		{
			@Override
			public String getName()
			{
				return base.getName();
			}
			
			@Override
			public void visit(BiConsumer<String, byte[]> consumer)
					throws IOException
			{
				base.visit((path, data) ->
				{
					if(filter.test(path))
						consumer.accept(path, data);
				});
			}
		};
	}
	
	static Source ofFile(File file)
	{
		return new Source()
		{
			@Override
			public String getName()
			{
				return file.getName();
			}
			
			@Override
			public void visit(BiConsumer<String, byte[]> consumer)
					throws IOException
			{
				IClassFileVisitor.visit(file, (p, h) -> consumer.accept(p, h.readAllBytes()));
			}
		};
	}
	
	static Source ofJrt()
	{
		return new JrtSource();
	}
}