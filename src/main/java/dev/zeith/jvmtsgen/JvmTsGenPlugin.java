package dev.zeith.jvmtsgen;

import dev.zeith.jvmtsgen.tasks.GenTypescriptTask;
import org.gradle.api.*;
import org.gradle.api.tasks.SourceSetContainer;

public class JvmTsGenPlugin
		implements Plugin<Project>
{
	@Override
	public void apply(Project project)
	{
		project.getTasks().register("genTypescript", GenTypescriptTask.class, t ->
				{
					t.notCompatibleWithConfigurationCache("Typescript generation is an optional task and should always run when called.");
					t.setGroup("build");
					
					t.getClasspath().from(project
							.getExtensions()
							.getByType(SourceSetContainer.class)
							.getByName("main")
							.getRuntimeClasspath()
					);
					
					t.getOutputDir().set(project
							.getLayout()
							.getBuildDirectory()
							.dir("generated/ts")
					);
				}
		);
	}
}
