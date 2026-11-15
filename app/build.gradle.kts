plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.pleos.contest.dashlink"
    compileSdk = 34
    useLibrary("android.car")
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "ai.pleos.contest.dashlink"
        minSdk = 28
        targetSdk = 34
        versionCode = 10
        versionName = "0.3.8-mvp"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    testImplementation("junit:junit:4.13.2")
}
