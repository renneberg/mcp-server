plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.graalvm.native)
    application
}

group = "org.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

application {
    mainClass.set("MainKt")
}

dependencies {
    implementation(dependencies.platform(libs.ktor.bom))
    implementation(libs.mcp.kotlin.server)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.slf4j.simple)
    
    // Ktor Client for WebFetcher
    implementation(libs.ktor.client.cio)

    // Kotlinx IO for Stdio Transport
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.3.0")

    // Database JDBC drivers (SQLite, MySQL, MariaDB)
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("com.mysql:mysql-connector-j:9.2.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")

    testImplementation(libs.mcp.kotlin.client)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
}

tasks.shadowJar {
    mergeServiceFiles()
    
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    
    append("META-INF/io.netty.versions.properties")
    
    exclude("META-INF/LICENSE.txt")
    exclude("META-INF/NOTICE.txt")
    exclude("META-INF/NOTICE")
    exclude("META-INF/LICENSE")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Copy>("copyShadowJarToLibs") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("libs"))
}

tasks.register<JavaExec>("queryData") {
    group = "application"
    description = "Queries MySQL for tmp__fulldata"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("QueryRunnerKt")
    if (project.hasProperty("dbName")) {
        args(project.property("dbName").toString())
    }
}

kotlin {
    jvmToolchain(17)
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("mcp-server")
            mainClass.set("MainKt")
            buildArgs.add("--no-fallback")
        }
    }
}
