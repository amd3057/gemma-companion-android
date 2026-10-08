import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().also { properties ->
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use { properties.load(it) }
    }
}
val uploadStoreFile = signingProperties.getProperty("storeFile")
val uploadStorePassword = signingProperties.getProperty("storePassword")
val uploadKeyAlias = signingProperties.getProperty("keyAlias")
val uploadKeyPassword = signingProperties.getProperty("keyPassword")
val releaseSigningConfigured = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
    .all { !it.isNullOrBlank() } && uploadStoreFile?.let { rootProject.file(it).isFile } == true

android {
    namespace = "com.amit.gemmcompanion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amit.gemmcompanion"
        minSdk = 31
        targetSdk = 36
        versionCode = providers.gradleProperty("APP_VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("APP_VERSION_NAME").orNull ?: "1.0.0"
    }

    if (releaseSigningConfigured) {
        signingConfigs {
            create("playRelease") {
                storeFile = rootProject.file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("playRelease")
            }
        }
    }
}

val verifyPlayUploadSigning = tasks.register("verifyPlayUploadSigning") {
    doLast {
        if (!releaseSigningConfigured) {
            throw GradleException(
                "Create a private root keystore.properties with storeFile, storePassword, keyAlias, " +
                    "and keyPassword before building a Play-signed release. See README.md."
            )
        }
    }
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyPlayUploadSigning)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("com.google.mediapipe:tasks-retrieval:1.1.0")

    testImplementation("junit:junit:4.13.2")
}