package dev.zeith.jvmtsgen.src;

import dev.zeith.jvmtsgen.util.LazyOptional;
import dev.zeith.tsgen.parse.IClassFileVisitor;

import java.io.*;
import java.util.function.Predicate;

public interface Source
{
	String getName();
	
	void visit(SourceEntryVisitor consumer)
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
			public void visit(SourceEntryVisitor consumer)
					throws IOException
			{
				base.visit((path, data, src) ->
				{
					if(filter.test(path))
						consumer.visit(path, data, src);
				});
			}
		};
	}
	
	static Source ofFile(File compiledJar, File sourceJar)
	{
		return new Source()
		{
			@Override
			public String getName()
			{
				return compiledJar.getName();
			}
			
			@Override
			public void visit(SourceEntryVisitor consumer)
					throws IOException
			{
				IClassFileVisitor.visit(compiledJar, sourceJar, (p, h, src) ->
						consumer.visit(p, h.readAllBytes(), src.map(s -> LazyOptional.of(() -> s)).orElseGet(LazyOptional::empty))
				);
			}
		};
	}
	
	static Source ofJrt()
	{
		return new JrtSource();
	}
}