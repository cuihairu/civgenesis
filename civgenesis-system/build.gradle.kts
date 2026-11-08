plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisDispatcher)
    api(projects.civgenesisCodecProtobuf)
    api(projects.civgenesisProtocolSystem)
    api(projects.civgenesisScheduler)
    api(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.awaitility)
    testImplementation(projects.civgenesisCore)
    testRuntimeOnly(libs.junit.platform.launcher)
}

