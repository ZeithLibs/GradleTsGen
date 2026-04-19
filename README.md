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
    id 'dev.zeith.gradle.JvmTsGen' version "1.1.5"
}
```

## Configuration
And here are some optional configuration options you can add:

```groovy
genTypescript {
    outputDir = file("build/generated/ts")
    cleanOutputDir = true
    importStrategy = dev.zeith.jvmtsgen.tasks.ImportStrategy.IMPORT_FROM
    includeJvm = true
    logSkippedClasses = false
    detailedErrorLog = false
    tsNoCheck = true
    classFilter = { it.startsWith("java/") }
}
```