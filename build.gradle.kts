plugins {
    java
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.projectreactor", "reactor-core", "3.4.3")
    testImplementation("org.testng", "testng", "7.4.0")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    test {
        useTestNG()
    }
}
