plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisIpc)
    api(libs.aeron.all)
    api(libs.slf4j.api)
}

