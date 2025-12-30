plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.protobuf.java)
    api(libs.protobuf.util)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
}
