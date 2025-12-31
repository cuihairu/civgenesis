plugins {
    `java-library`
}

dependencies {
    api(projects.civgenesisJobs)
    api(libs.lettuce.core)
    api(libs.slf4j.api)
}

