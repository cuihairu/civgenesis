plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisCore)
    api(projects.civgenesisProtocolSystem)
    api(libs.protobuf.java)
    api(libs.protobuf.util)
}

