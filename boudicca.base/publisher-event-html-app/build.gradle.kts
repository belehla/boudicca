plugins {
    id("boudicca-springboot-app")
}

dependencies {
    implementation(project(":boudicca.base:publisher-event-html"))
}

docker {
    imageName = "publisher-event-html"
}
