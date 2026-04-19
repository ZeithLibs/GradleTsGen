package dev.zeith.jvmtsgen.src;

import dev.zeith.jvmtsgen.util.LazyOptional;

public interface SourceEntryVisitor
{
	void visit(String path, byte[] bytecode, LazyOptional<String> sourceCode);
}