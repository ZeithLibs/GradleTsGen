package dev.zeith.jvmtsgen.util;

import dev.zeith.jvmtsgen.tasks.ImportStrategy;
import dev.zeith.tsgen.TSGenSettings;
import lombok.*;
import org.jetbrains.annotations.*;

import java.util.Objects;

@Setter
@Getter
public class GeneratorSettings
{
	@NotNull ImportStrategy importStrategy = ImportStrategy.IMPORT_FROM;
	@Nullable Boolean enableVarargs;
	@Nullable String newline = "\n";
	@Nullable String indent = "\t";
	boolean logSkippedClasses;
	boolean detailedErrorLog;
	boolean noTsCheck = true;
	
	public void apply(TSGenSettings.TSGenSettingsBuilder tsg)
	{
		if(enableVarargs != null) tsg.enableVarargs(enableVarargs);
		if(isPresent(newline)) tsg.newline(newline);
		if(isPresent(indent)) tsg.indent(indent);
	}
	
	@Contract("null -> false; _ -> _")
	private boolean isPresent(String s)
	{
		return !"null".equals(Objects.toString(s));
	}
}