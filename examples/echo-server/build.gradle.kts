plugins {
    application
    java
    alias(libs.plugins.protobuf)
}

dependencies {
    implementation(projects.civgenesisSpringBootStarter)
    implementation(libs.spring.boot.starter)
    implementation(libs.protobuf.java)
}

application {
    mainClass.set("io.github.cuihairu.civgenesis.examples.echo.EchoServerApplication")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
}
