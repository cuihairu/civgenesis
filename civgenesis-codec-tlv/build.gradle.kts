plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisCore)
    api(platform(libs.netty.bom))
    api(libs.netty.buffer)
    api(libs.netty.common)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
