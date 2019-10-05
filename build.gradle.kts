plugins {
    java
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compile("io.projectreactor", "reactor-core", "3.2.9.RELEASE")
    testCompile("org.testng", "testng", "7.0.0")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    test {
        useTestNG()
    }
}
