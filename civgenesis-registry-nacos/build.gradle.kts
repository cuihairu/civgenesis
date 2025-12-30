plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisRegistry)
    api(libs.nacos.client)
    api(libs.slf4j.api)
}

