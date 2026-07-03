plugins {
    application
    id("com.gradleup.shadow") version "8.3.6"
}

group = "io.oatg"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.swagger.parser.v3:swagger-parser:2.1.25")
    implementation("com.github.curious-odd-man:rgxgen:2.0")
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("net.datafaker:datafaker:2.4.2")
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.16")

    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.wiremock:wiremock:3.10.0")
}

application {
    mainClass = "io.oatg.Main"
}

tasks.jar {
    archiveClassifier = "plain" // the fat JAR from shadowJar owns the plain name
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}

tasks.shadowJar {
    archiveBaseName = "oatg"
    archiveClassifier = ""
    mergeServiceFiles()
}

// the fat JAR is the only distribution we ship
listOf("distZip", "distTar", "shadowDistZip", "shadowDistTar").forEach {
    tasks.named(it) { enabled = false }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        showStackTraces = true
    }
}
