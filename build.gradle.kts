plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // DreamBot client JAR on local filesystem, parameterized via gradle property or env
    // Usage: ./gradlew -PclientJar=/path/to/client.jar build
    val clientJarPath = (project.findProperty("clientJar") as String?)
        ?: System.getenv("CLIENT_JAR")
        ?: System.getProperty("client.jar")
        ?: "${System.getProperty("user.home")}/DreamBot/BotData/client.jar"
    compileOnly(files(clientJarPath))
}

tasks.register<Jar>("helloWorldJar") {
    group = "build"
    description = "Package HelloWorld script"
    from(sourceSets.main.get().output)
    include("scripts/HelloWorld.class")
    archiveFileName.set("HelloWorld-1.1.jar")
}

tasks.register<Jar>("dommiksNeedlesJar") {
    group = "build"
    description = "Package Dommiks Needles script"
    from(sourceSets.main.get().output)
    include("scripts/DommiksNeedles.class")
    archiveFileName.set("DommiksNeedles-1.1.jar")
}

tasks.register<Jar>("logLocationJar") {
    group = "build"
    description = "Package LogLocation script"
    from(sourceSets.main.get().output)
    include("scripts/LogLocation.class")
    archiveFileName.set("LogLocation-1.1.jar")
}

tasks.register<Copy>("installJars") {
    dependsOn("classes", "helloWorldJar", "dommiksNeedlesJar", "logLocationJar")
    from(layout.buildDirectory.file("libs/HelloWorld-1.1.jar"))
    from(layout.buildDirectory.file("libs/DommiksNeedles-1.1.jar"))
    from(layout.buildDirectory.file("libs/LogLocation-1.1.jar"))
    into("${System.getProperty("user.home")}/DreamBot/Scripts")
}
