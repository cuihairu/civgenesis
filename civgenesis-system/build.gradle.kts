plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisDispatcher)
    api(projects.civgenesisCodecProtobuf)
    api(projects.civgenesisProtocolSystem)
    api(projects.civgenesisScheduler)
    api(libs.slf4j.api)
}

