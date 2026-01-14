plugins {
    java
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.projectreactor", "reactor-core", "3.8.2")
    implementation("io.projectreactor.addons", "reactor-pool", "1.2.2")
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", "6.0.2")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    test {
        useJUnitPlatform()
    }
}
