import com.android.build.api.artifact.SingleArtifact
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// ---------------------------------------------------------------------------
// Build-time guards
// ---------------------------------------------------------------------------

/**
 * Fails the build if the merged manifest requests any permission other than the allow-list.
 * Kryptex is zero-network: a library that tries to merge in INTERNET (or anything else)
 * must break the build instead of silently shipping.
 */
abstract class VerifyManifestPermissions : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val allowedPermissions: ListProperty<String>

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun verify() {
        val text = manifest.get().asFile.readText()
        val declared = Regex("""<uses-permission(?:-sdk-23)?\s[^>]*?android:name="([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSortedSet()
        val allowed = allowedPermissions.get().toSet()
        val illegal = declared.filterNot { it in allowed }
        if (illegal.isNotEmpty()) {
            throw GradleException(
                "Merged manifest requests permissions outside the Kryptex allow-list: $illegal. " +
                    "Remove them with tools:node=\"remove\" in AndroidManifest.xml, or drop the library that adds them."
            )
        }
        stamp.get().asFile.apply {
            parentFile.mkdirs()
            writeText("ok: ${declared.joinToString()}\n")
        }
    }
}

/** Fails the build if the bundled MediaPipe model does not match the pinned SHA-256. */
abstract class VerifyModelChecksum : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val model: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val expected: RegularFileProperty

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun verify() {
        val digest = MessageDigest.getInstance("SHA-256")
        model.get().asFile.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        val want = expected.get().asFile.readText().trim().lowercase()
        if (actual != want) {
            throw GradleException("face_landmarker.task checksum mismatch: expected $want, got $actual")
        }
        stamp.get().asFile.apply {
            parentFile.mkdirs()
            writeText("ok: $actual\n")
        }
    }
}

val verifyFaceModel = tasks.register<VerifyModelChecksum>("verifyFaceModel") {
    model.set(layout.projectDirectory.file("src/main/assets/face_landmarker.task"))
    expected.set(layout.projectDirectory.file("face_landmarker.task.sha256"))
    stamp.set(layout.buildDirectory.file("verify/face-model.ok"))
}

tasks.named("preBuild") { dependsOn(verifyFaceModel) }

// ---------------------------------------------------------------------------
// Android
// ---------------------------------------------------------------------------

android {
    namespace = "com.kryptex.vault"
    // Current Compose / Lifecycle / Core releases require compiling against API 37.
    // targetSdk stays at 36, so runtime behaviour changes are still opted into deliberately.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kryptex.vault"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The MediaPipe model is memory-mapped straight out of the APK; it must stay uncompressed.
    androidResources {
        noCompress += "task"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val verifyPermissions = tasks.register<VerifyManifestPermissions>("verifyManifestPermissions$cap") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowedPermissions.set(
                listOf(
                    "android.permission.CAMERA",
                    // Gesture-recognised vibration pulse (normal permission, no user prompt).
                    "android.permission.VIBRATE",
                    // Added by androidx.core for non-exported dynamic receivers; app-private signature permission.
                    "${android.defaultConfig.applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                )
            )
            stamp.set(layout.buildDirectory.file("verify/manifest-permissions-${variant.name}.ok"))
        }
        tasks.configureEach {
            if (name == "assemble$cap" || name == "bundle$cap") dependsOn(verifyPermissions)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    implementation(libs.argon2kt)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
