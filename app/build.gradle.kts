plugins {
    alias(libs.plugins.android.application)
}

android {
    signingConfigs {
        create("release") { }
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

/** Kill duplicate classes by excluding the old IntelliJ annotations everywhere. */
configurations.configureEach {
    exclude(group = "com.intellij", module = "annotations")
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
    implementation(libs.runner)
    implementation(libs.play.services.location)
    implementation(libs.swiperefreshlayout)

    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler) // <-- was implementation(...)

    debugImplementation(libs.stetho)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.slf4j.simple)
    testImplementation("org.json:json:20230227") // For unit tests with JSON
    androidTestImplementation(libs.espresso.core)

    // implementation("com.github.tcheeric:nostr-java:main-SNAPSHOT")
}
