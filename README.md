# Java Typescript Generator (Gradle Plugin)

This [TsFacadeGenerator](https://github.com/ZeithLibs/TsFacadeGenerator) as its backbone to generate typescript from the current classpath.

You can find the `genTypescript` task in `build` category.

## Installation:

- `settings.gradle`: Add these two repositories into pluginManagement
```groovy
pluginManagement {
    repositories {
        mavenCentral()
        maven { url = 'https://maven.zeith.org/' }
    }
}
```

- `build.gradle`: 
```groovy
plugins {
    id 'dev.zeith.gradle.JvmTsGen' version "1.2.0"
}
```

## Configuration
And here are some optional configuration options you can add:

```groovy
genTypescript {
    // The output directory for the typescript.
    outputDir = file("build/generated/ts")
    
    // Should the outputDir be completely wiped before running generation?
    cleanOutputDir = true

    // Which import strategy should be used for TS facade generation?
    // IMPORT_FROM -> import { Object } from "java/lang.ts";
    // REQUIRE -> const { Object } = require("java/lang.ts");
    // VisualStudio usually prefers IMPORT_FROM, and if not set, this is the default value for the task.
    importStrategy = dev.zeith.jvmtsgen.tasks.ImportStrategy.IMPORT_FROM

    // Include gradle's build Java modules into TS facade generation generation?
    includeJvm = true

    // Prints which classes failed to generate TS facade.
    logSkippedClasses = false
    
    // For debugging purposes: prints the stack trace when class parsing fails, only used when logSkippedClasses is true
    detailedErrorLog = false

    // add // @ts-nocheck at the top of every generated file
    tsNoCheck = true
    
    // set to > 0 if you experience out of memory
    maxQueueSize = 0
    
    // Filters the classes to be included into generation as a path predicate
    classFilter = { it.startsWith("java/") }

    // Typescript generation extensions.
    // If the extension is included into both enabled and disabled list, it will be active.
    // Currently supported extensions:
    // - 'org.mozilla:rhino:__javaObject__' - Adds static Class<T> field __javaObject__ into every declare class. Useful for interop with Mozilla Rhino
    enabledExtensions = ['org.mozilla:rhino:__javaObject__']
    disabledExtensions = []
}
```