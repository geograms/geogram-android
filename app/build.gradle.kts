plugins {
    alias(libs.plugins.android.application)
}

android {
    signingConfigs {
        create("release") {
        }
    }
    namespace = "offgrid.geogram"
    compileSdk = 34

    defaultConfig {
        applicationId = "offgrid.geogram"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "chat testing"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        applicationIdSuffix = "geogram"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.play.services.nearby)
    implementation(libs.spark.core)
    implementation(libs.car.ui.lib)
    implementation(libs.gson)
    implementation(libs.bcprov.jdk15on)
    implementation(libs.bcpkix.jdk15on)
    implementation(libs.viewpager2)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.slf4j.simple)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Add the nostr-java-api library
    //implementation("com.github.tcheeric:nostr-java:main-SNAPSHOT")
}
