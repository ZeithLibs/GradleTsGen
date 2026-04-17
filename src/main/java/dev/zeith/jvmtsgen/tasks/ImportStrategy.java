package dev.zeith.jvmtsgen.tasks;

import dev.zeith.tsgen.imports.*;
import lombok.Getter;

public enum ImportStrategy
{
	IMPORT_FROM(FromImportModel.INSTANCE),
	REQUIRE(RequireImportModel.INSTANCE);
	
	@Getter
	private final BaseImportModel importModel;
	
	ImportStrategy(BaseImportModel importModel)
	{
		this.importModel = importModel;
	}
}