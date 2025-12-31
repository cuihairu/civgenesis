plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisCore)
    api(projects.civgenesisCodecTlv)
    api(platform(libs.netty.bom))
    api(libs.netty.buffer)
    api(libs.slf4j.api)

    testImplementation(projects.civgenesisCodecProtobuf)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
