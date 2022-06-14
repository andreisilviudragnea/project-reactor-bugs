plugins {
    java
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.projectreactor", "reactor-core", "3.4.18")
    implementation("io.projectreactor.addons", "reactor-pool", "0.2.9")
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", "5.8.2")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    test {
        useJUnitPlatform()
    }
}
