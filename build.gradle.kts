plugins {
    java
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.projectreactor", "reactor-core", "3.7.4")
    implementation("io.projectreactor.addons", "reactor-pool", "1.1.2")
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", "5.12.1")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    test {
        useJUnitPlatform()
    }
}
