rootProject.name = "civgenesis"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":civgenesis-core",
    ":civgenesis-codec-tlv",
    ":civgenesis-dispatcher",
    ":civgenesis-scheduler",
    ":civgenesis-jobs",
    ":civgenesis-jobs-lease-redis",
    ":civgenesis-system",
    ":civgenesis-transport-netty-ws",
    ":civgenesis-transport-netty-tcp",
    ":civgenesis-ipc",
    ":civgenesis-ipc-aeron",
    ":civgenesis-protocol-system",
    ":civgenesis-codec-protobuf",
    ":civgenesis-registry",
    ":civgenesis-registry-nacos",
    ":civgenesis-rpc-grpc",
    ":civgenesis-spring-boot-starter",
    ":examples-echo-server",
)

project(":examples-echo-server").projectDir = file("examples/echo-server")
