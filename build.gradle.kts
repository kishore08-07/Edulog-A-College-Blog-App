// Project-level build.gradle file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

buildscript {
    repositories {
        google()  // Required for Google services Gradle plugin
        mavenCentral()
    }
    
    dependencies {
        // Firebase plugin classpath
        classpath("com.google.gms:google-services:4.4.1") // latest as of now
    }
}
