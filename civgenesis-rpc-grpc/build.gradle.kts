plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisRegistry)
    api(libs.grpc.netty.shaded)
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.protobuf.java)
    api(libs.slf4j.api)
}

