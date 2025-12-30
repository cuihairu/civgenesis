plugins {
    base
    jacoco
}

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("java-library") {
        apply(plugin = "jacoco")

        extensions.configure<JavaPluginExtension>("java") {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
            withJavadocJar()
            withSourcesJar()
        }
        extensions.configure<JacocoPluginExtension>("jacoco") {
            toolVersion = "0.8.12"
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            finalizedBy(tasks.named("jacocoTestReport"))
        }
        tasks.withType<JacocoReport>().configureEach {
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
    }
}

val jacocoRootReport = tasks.register<JacocoReport>("jacocoRootReport") {
    group = "verification"
    description = "Aggregated Jacoco coverage report."

    val javaProjects = subprojects.filter { it.plugins.hasPlugin("java") || it.plugins.hasPlugin("java-library") }
    dependsOn(javaProjects.mapNotNull { it.tasks.findByName("test") })

    val execFiles = javaProjects.map { p ->
        p.fileTree(p.layout.buildDirectory.asFile.get()).matching {
            include("jacoco/test.exec", "jacoco/test*.exec")
        }
    }
    executionData.from(execFiles)

    val mainClassTrees = javaProjects.mapNotNull { p ->
        val sourceSets = p.extensions.findByType<SourceSetContainer>() ?: return@mapNotNull null
        sourceSets.named("main").get().output.asFileTree.matching {
            exclude("**/io/github/cuihairu/civgenesis/protocol/system/**")
            exclude("**/SystemMessages*.class")
        }
    }
    classDirectories.from(mainClassTrees)

    val sources = javaProjects.mapNotNull { p ->
        val sourceSets = p.extensions.findByType<SourceSetContainer>() ?: return@mapNotNull null
        sourceSets.named("main").get().allSource.srcDirs
    }
    sourceDirectories.from(sources)

    reports {
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/root/jacocoRootReport.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/root/html"))
    }
}
