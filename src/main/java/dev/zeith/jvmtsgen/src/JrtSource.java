package dev.zeith.jvmtsgen.src;

import dev.zeith.jvmtsgen.util.LazyOptional;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;
import java.util.zip.*;

public class JrtSource
		implements Source
{
	private final FileSystem jrtFs;
	private final File srcJar;
	
	public JrtSource()
	{
		try
		{
			this.jrtFs = FileSystems.getFileSystem(URI.create("jrt:/"));
			
			String javaHome = System.getProperty("java.home");
			File javaHomeDir = new File(javaHome);
			File srcJar = new File(javaHomeDir, "lib/src.zip");
			if(!srcJar.exists()) srcJar = new File(javaHomeDir, "lib/src.jar");
			this.srcJar = srcJar.isFile() ? srcJar : null;
		} catch(Exception e)
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
	public void visit(SourceEntryVisitor consumer)
	{
		try(var zip = srcJar != null ? new ZipFile(srcJar) : null)
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
						 
						 String moduleName = path.subpath(0, 1).toString();
						 
						 // remove java.base
						 path = path.subpath(1, path.getNameCount());
						 
						 String name = path.toString();
						 
						 LazyOptional<String> sourceCode = LazyOptional.empty();
						 if(zip != null)
						 {
							 String sourceFilePath = name.substring(0, name.length() - 6);
							 int filename = sourceFilePath.lastIndexOf(47) + 1;
							 int subclass = sourceFilePath.indexOf(36, filename);
							 if(subclass != -1) sourceFilePath = sourceFilePath.substring(0, subclass);
							 ZipEntry srcEntry = zip.getEntry(moduleName + "/" + sourceFilePath + ".java");
							 if(srcEntry != null && !srcEntry.isDirectory())
							 {
								 try(var in = zip.getInputStream(srcEntry))
								 {
									 byte[] data = in.readAllBytes();
									 sourceCode = LazyOptional.of(() -> new String(data, StandardCharsets.UTF_8));
								 } catch(Exception e) {}
							 }
						 }
						 
						 consumer.visit(name, bytes, sourceCode);
					 } catch(Exception ignored) {}
				 });
		} catch(Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}