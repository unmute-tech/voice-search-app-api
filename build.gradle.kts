import io.gitlab.arturbosch.detekt.extensions.DetektExtension.Companion.DEFAULT_SRC_DIR_JAVA
import io.gitlab.arturbosch.detekt.extensions.DetektExtension.Companion.DEFAULT_SRC_DIR_KOTLIN
import io.gitlab.arturbosch.detekt.extensions.DetektExtension.Companion.DEFAULT_TEST_SRC_DIR_JAVA
import io.gitlab.arturbosch.detekt.extensions.DetektExtension.Companion.DEFAULT_TEST_SRC_DIR_KOTLIN
import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
  application
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.sqldelight)
  alias(libs.plugins.detekt)
  alias(libs.plugins.ktor)
}

sqldelight {
  databases {
    create("BanjaraDb") {
      packageName.set("io.reitmaier.banjaraapi.db")
      dialect(libs.sqldelight.postgresql.get())
      deriveSchemaFromMigrations.set(true)
    }
  }
}

group = "io.reitmaier"
version = "0.0.1"
application {
  mainClass.set("io.ktor.server.netty.EngineMain")

}

repositories {
  mavenCentral()
}

java {
  // align with ktor/docker JRE
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}
tasks {
  withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = "${JavaVersion.VERSION_17}"
    }
  }
}

dependencies {
  // ktor
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.server.auth.jwt)
  implementation(libs.ktor.server.auto.head.response)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.default.headers)
  implementation(libs.ktor.server.partial.content)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.server.core.jvm)
  implementation(libs.ktor.server.netty.jvm)
  implementation(libs.ktor.server.html.builder)

  // db -- config
  implementation(libs.hikari)

  // db -- postgres
  implementation(libs.postgresql)

  // db -- sqldelight
  implementation(libs.sqldelight.jdbc)
  implementation(libs.sqldelight.coroutines)

  // utilities
  implementation(libs.kotlinx.datetime)
  implementation(libs.google.guava)

  // logging
  implementation(libs.ktor.server.call.logging)
  implementation(libs.logback.classic)
  implementation(libs.kotlin.inline.logger)

  // Coroutines
  implementation(libs.kotlinx.coroutines.core)

  // Google Cloud Speech-to-Text & Translate
  implementation(libs.google.cloud.speech)
  implementation(libs.google.cloud.translate)

  // Result monad for modelling success/failure operations
  implementation(libs.kotlin.result)
  implementation(libs.kotlin.result.coroutines)

  // testing
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.ktor.server.tests.jvm)
}

class PrivateImageRegistry(
  registryUrl: Provider<String>,
  imageName: String,
  override val username: Provider<String>,
  override val password: Provider<String>,
): DockerImageRegistry {
  override val toImage: Provider<String> = provider {
    "${registryUrl.get().substringAfter("://").trim('/')}/$imageName"
  }
}

ktor {
  docker {
    // align with java config
    val dockerImageName = "unmutelaunchapi"
    jreVersion.set(JreVersion.JRE_17)
    localImageName.set(dockerImageName)
    imageTag.set("latest")
    externalRegistry.set(
      PrivateImageRegistry(
        providers.environmentVariable("PRIVATE_DOCKER_REGISTRY_URL"),
        dockerImageName,
        providers.environmentVariable("PRIVATE_DOCKER_REGISTRY_USER"),
        providers.environmentVariable("PRIVATE_DOCKER_REGISTRY_PASSWORD"),
      )
    )
    // can also use DockerImageRegistry.dockerHub()
    // or just use local image and delete external registry
  }
}
task<Exec>("deploy") {
  dependsOn("publishImage")
  commandLine("bash","./redeploy.sh")
}

allprojects {
  apply {
    plugin(rootProject.libs.plugins.detekt.get().pluginId)
  }

  dependencies {
    detektPlugins(rootProject.libs.io.gitlab.arturbosch.detekt.formatting)
  }

  detekt {
    source = files(
      "src",
      DEFAULT_SRC_DIR_JAVA,
      DEFAULT_TEST_SRC_DIR_JAVA,
      DEFAULT_SRC_DIR_KOTLIN,
      DEFAULT_TEST_SRC_DIR_KOTLIN,
    )
    toolVersion = rootProject.libs.versions.detekt.get()
    config = rootProject.files("config/detekt/detekt.yml")
    parallel = true
  }
}

