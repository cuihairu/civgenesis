plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisRegistry)
    api(libs.nacos.client)
    api(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

