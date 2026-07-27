plugins {
    java
}

group = property("group") as String
version = property("version") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // TrinityForge 統合アドオン API（ビルド済みjar・compileOnly。実行時はサーバーが先にロード）
    compileOnly(files("libs/TrinityForge.jar"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}


tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    includeEmptyDirs = false
    exclude("**/*.bak-*")
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("ArsPaper")
}

tasks.test {
    useJUnitPlatform()
}
