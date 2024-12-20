plugins {
    kotlin("jvm") version "1.9.0"
    java
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:lincheck:2.34")
    testImplementation("javax.xml.bind:jaxb-api:2.3.1")
}

repositories {
    mavenCentral()
}

sourceSets.main {
    java.srcDir("src")
}

sourceSets.test {
    java.srcDir("test")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    test {
        maxHeapSize = "6g"
        jvmArgs(
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-exports", "java.base/jdk.internal.util=ALL-UNNAMED",
            "--add-exports", "java.base/sun.security.action=ALL-UNNAMED"
        )
    }
}