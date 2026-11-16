// AGP 9 ships built-in Kotlin support (pinned to an older Kotlin by default).
// Putting the Kotlin Gradle plugin on the buildscript classpath lets the version
// catalog decide which Kotlin compiler is actually used.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
