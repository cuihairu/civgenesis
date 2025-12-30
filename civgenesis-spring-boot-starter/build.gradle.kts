plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisTransportNettyWs)
    api(projects.civgenesisCodecProtobuf)
    api(projects.civgenesisRegistryNacos)
    api(projects.civgenesisRpcGrpc)
    api(projects.civgenesisJobs)
    api(libs.spring.boot.autoconfigure)
    api(libs.micrometer.core)
    api(libs.micrometer.registry.prometheus)
    api(libs.opentelemetry.api)
    api(libs.opentelemetry.context)
    implementation(libs.spring.boot.starter)

    annotationProcessor(libs.spring.boot.configuration.processor)
}
