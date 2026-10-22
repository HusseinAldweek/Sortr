import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.sortr.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sortr.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // One APK per processor type, since ML Kit's native code is about 35 MB per type: most phones
    // need the arm64-v8a one. The universal APK holds all three, for when you're not sure.
    // 32-bit x86 only exists on old emulators and is left out.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Personal, sideloaded app: release builds are signed with the local debug key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Keeps the universal APK to the three types above.
        jniLibs {
            excludes += "lib/x86/**"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

/**
 * Sortr must never reach the network. Libraries can add permissions through manifest merging,
 * so this fails the build if any network permission survives into the final manifest.
 */
abstract class VerifyNoNetworkPermissions : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val banned = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
        )
        val manifest = mergedManifest.get().asFile.readText()
        val declared = Regex("""<uses-permission[^>]*?android:name="([^"]+)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toList()
        val offending = declared.filter { it in banned }
        if (offending.isNotEmpty()) {
            throw GradleException(
                "Sortr must stay offline, but the merged manifest requests: " +
                    offending.joinToString() +
                    ". Add a tools:node=\"remove\" entry for it in app/src/main/AndroidManifest.xml.",
            )
        }
        // Upload backends for Google's usage-logging library (pulled in by ML Kit).
        val telemetryBackends = Regex("""android:name="(backend:[^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        if (telemetryBackends.isNotEmpty()) {
            throw GradleException(
                "Sortr must stay offline, but the merged manifest registers telemetry upload backends: " +
                    telemetryBackends.joinToString() +
                    ". Remove them with tools:node=\"remove\" in app/src/main/AndroidManifest.xml.",
            )
        }
        report.get().asFile.writeText(declared.joinToString(separator = "\n", postfix = "\n"))
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyNoNetworkPermissions>("verify${variantName}NoNetworkPermissions") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
        }
        // Packaging covers assemble, install and bundle.
        tasks.matching { it.name == "package$variantName" }.configureEach { dependsOn(verify) }
    }
}
