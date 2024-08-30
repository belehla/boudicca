plugins {
    id("boudicca-springboot-app")
}

dependencies {
    implementation("com.google.api-client:google-api-client:2.7.0") {
        exclude("commons-logging", "commons-logging")
    }
    implementation("com.google.auth:google-auth-library-oauth2-http:1.24.1") {
        exclude("commons-logging", "commons-logging")
    }
    implementation("com.google.apis:google-api-services-sheets:v4-rev20240826-2.0.0") {
        exclude("commons-logging", "commons-logging")
    }

    implementation(project(":boudicca.base:enricher-api"))
    implementation(project(":boudicca.base:common-model"))
}