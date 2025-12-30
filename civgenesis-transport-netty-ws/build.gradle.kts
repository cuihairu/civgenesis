plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisCore)
    api(projects.civgenesisCodecTlv)
    api(projects.civgenesisDispatcher)
    api(projects.civgenesisScheduler)

    api(platform(libs.netty.bom))
    api(libs.netty.transport)
    api(libs.netty.handler)
    api(libs.netty.codec)
    api(libs.netty.codec.http)
    api(libs.netty.buffer)
    api(libs.netty.common)

    api(libs.slf4j.api)
}

