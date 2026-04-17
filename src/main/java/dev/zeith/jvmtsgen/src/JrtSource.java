package dev.zeith.jvmtsgen.src;

import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

public class JrtSource implements Source
{
	private final FileSystem jrtFs;
	
	public JrtSource()
	{
		try
		{
			this.jrtFs = FileSystems.getFileSystem(URI.create("jrt:/"));
		} catch (Exception e)
		{
			throw new RuntimeException("Failed to open jrt filesystem", e);
		}
	}
	
	@Override
	public String getName()
	{
		return "JRT";
	}
	
	@Override
	public void visit(BiConsumer<String, byte[]> consumer)
	{
		try
		{
			Path modules = jrtFs.getPath("/modules");
			Files.walk(modules)
				 .filter(p -> p.toString().endsWith(".class"))
				 .forEach(path ->
				 {
					 try
					 {
						 byte[] bytes = Files.readAllBytes(path);
						 
						 // relative path
						 path = modules.relativize(path);
						 
						 // remove java.base
						 path = path.subpath(1, path.getNameCount());
						 
						 String name = path.toString();
						 consumer.accept(name, bytes);
					 }
					 catch (Exception ignored) {}
				 });
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}