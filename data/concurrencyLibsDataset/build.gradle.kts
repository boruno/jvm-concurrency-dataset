plugins {
    java
    kotlin("jvm") version "1.8.10"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.jctools:jctools-core:4.0.3")
    implementation("com.googlecode.concurrent-trees:concurrent-trees:2.6.1")
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-common"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-util:9.6")
    implementation("net.bytebuddy:byte-buddy:1.14.12")
    implementation("net.bytebuddy:byte-buddy-agent:1.14.12")
    implementation("org.jetbrains.kotlinx:atomicfu:0.19.0")
    testImplementation("junit:junit:4.13.1")
    testImplementation("org.jetbrains.kotlinx:lincheck:2.39")
    testImplementation("org.jetbrains.kotlinx:atomicfu:0.18.3")
    testImplementation("com.google.guava:guava:33.2.1-jre")
    testImplementation("org.agrona:agrona:1.22.0")
    testImplementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    jvmArgs("-Xmx4g")
}

tasks.test {
    useJUnit()
}