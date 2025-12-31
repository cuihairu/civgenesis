plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisRegistry)
    api(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
