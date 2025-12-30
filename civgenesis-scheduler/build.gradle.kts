plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisCore)
    api(platform(libs.netty.bom))
    api(libs.netty.common)
}

