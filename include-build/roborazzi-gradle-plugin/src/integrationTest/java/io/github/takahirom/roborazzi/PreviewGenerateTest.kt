package io.github.takahirom.roborazzi

import org.gradle.testkit.runner.BuildResult
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeneratePreviewTestTest {
  @get:Rule
  val testProjectDir = TemporaryFolder()

  @Test
  fun whenRecordRunImagesShouldBeRecorded() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      record()

      checkHasImages()
    }
  }
  @Test
  fun whenKmpModuleAndRecordRunImagesShouldBeRecorded() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.isKmp = true
      record()

      checkHasImages()
    }
  }
}

class PreviewModule(
  val rootProject: RoborazziGradleRootProject,
  val testProjectDir: TemporaryFolder
) {
  companion object {
    val moduleName = "sample-generate-preview-tests"
  }
  val buildGradle = BuildGradle(testProjectDir)

  class BuildGradle(private val projectFolder: TemporaryFolder) {
    private val PATH = moduleName + "/build.gradle.kts"
    var isKmp = false
    fun write() {
      val file =
        projectFolder.root.resolve(PATH)
      file.parentFile.mkdirs()
      val roborazziExtension = """
          roborazzi {
            generateComposePreviewRobolectricTests {
              enable = true
              packages = listOf("com.github.takahirom.preview.tests")
            }
          }
      """.trimIndent()
      val androidBlock = """
          android {
            namespace = "com.github.takahirom.preview.tests"
            compileSdk = 34

            defaultConfig {
              minSdk = 24

              testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            buildFeatures {
              compose = true
            }
            composeOptions {
              kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
            }

            buildTypes {
              release {
                isMinifyEnabled = false
                proguardFiles(
                  getDefaultProguardFile("proguard-android-optimize.txt"),
                  "proguard-rules.pro"
                )
              }
            }
            testOptions {
              unitTests {
                isIncludeAndroidResources = true
                all {
                  it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
                }
              }
            }
          }

      """.trimIndent()
      val buildGradleText = if(isKmp)
        """
          plugins {
              kotlin("multiplatform")
              id("com.android.library")
              id("org.jetbrains.compose")
              id("io.github.takahirom.roborazzi")
          }

          kotlin {
              androidTarget()
              
              sourceSets {
                  val commonMain by getting {
                      dependencies {
                          api(compose.components.uiToolingPreview)
                      }
                  }
                  val androidMain by getting {
                      dependencies {
                          implementation(compose.material3)
                          implementation(compose.ui)
                          implementation(compose.uiTooling)
                          implementation(compose.runtime)
                      }
                  }
                  
                  val androidUnitTest by getting {
                      dependencies {
                          implementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:0.1.0")
                          implementation(libs.junit)
                          implementation(libs.robolectric)
                          implementation(libs.composable.preview.scanner)
                      }
                  }
                  
                  val androidInstrumentedTest by getting {
                      dependencies {
                          implementation(libs.androidx.test.ext.junit)
                          implementation(libs.androidx.test.espresso.core)
                      }
                  }
              }
          }

          $androidBlock

          $roborazziExtension

          repositories {
              mavenCentral()
              google()
              maven { url = uri("https://jitpack.io") }
          }
        """.trimIndent()
        else """
  plugins {
    id("com.android.application")
  //  id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("io.github.takahirom.roborazzi")
  }

  $roborazziExtension

  repositories {
    mavenCentral()
    google()
    maven { url = uri("https://jitpack.io") }
  }
  $androidBlock

  dependencies {
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.runtime)

    // replaced by dependency substitution
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:0.1.0")
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.composable.preview.scanner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
  }
"""
      file.writeText(
        buildGradleText.trimIndent()
      )
    }
  }

  fun record() {
    runTask("recordRoborazziDebug")
  }

  private fun runTask(
    task: String,
    buildType: BuildType = BuildType.Build,
    additionalParameters: Array<String> = arrayOf()
  ): BuildResult {
    buildGradle.write()
    val buildResult = rootProject.runTask(
      ":$moduleName:" + task,
      buildType,
      additionalParameters
    )
    return buildResult
  }

  fun checkHasImages() {
    val images = testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/").listFiles()
    println("images:" + images?.toList())
    assert(images?.isNotEmpty() == true)
  }
}