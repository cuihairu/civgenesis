plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisCore)
    api(projects.civgenesisCodecTlv)
    api(platform(libs.netty.bom))
    api(libs.netty.buffer)
    api(libs.slf4j.api)
}

