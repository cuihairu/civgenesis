plugins {
    `java-library`
}

dependencies {
    api(platform(libs.netty.bom))
    api(libs.netty.buffer)
    api(libs.slf4j.api)
}

