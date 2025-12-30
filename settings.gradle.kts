rootProject.name = "civgenesis"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":civgenesis-core",
    ":civgenesis-codec-tlv",
    ":civgenesis-dispatcher",
    ":civgenesis-scheduler",
    ":civgenesis-jobs",
    ":civgenesis-system",
    ":civgenesis-transport-netty-ws",
    ":civgenesis-protocol-system",
    ":civgenesis-codec-protobuf",
    ":civgenesis-registry",
    ":civgenesis-registry-nacos",
    ":civgenesis-rpc-grpc",
    ":civgenesis-spring-boot-starter",
)
