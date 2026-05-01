package dev.zeith.jvmtsgen.util;

import dev.zeith.jvmtsgen.tasks.ImportStrategy;
import dev.zeith.tsgen.TSGenSettings;
import lombok.*;
import org.jetbrains.annotations.*;

@Setter
@Getter
public class GeneratorSettings
{
	@NotNull ImportStrategy importStrategy = ImportStrategy.IMPORT_FROM;
	@Nullable Boolean enableVarargs;
	@Nullable String newline;
	@Nullable String indent;
	boolean logSkippedClasses;
	boolean detailedErrorLog;
	boolean noTsCheck = true;
	
	public void apply(TSGenSettings.TSGenSettingsBuilder tsg)
	{
		if(enableVarargs != null) tsg.enableVarargs(enableVarargs);
		if(newline != null) tsg.newline(newline);
		if(indent != null) tsg.indent(indent);
	}
}