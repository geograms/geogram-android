plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
        versionCode = 23
        versionName = "0.5.23"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        applicationIdSuffix = "geogram"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packagingOptions {
        resources {
            // Exclude duplicate META-INF files from BouncyCastle libraries
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST",
                "META-INF/license/*",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                // Cactus SDK excludes
                "/META-INF/{AL2.0,LGPL2.1}",
                "/com/sun/jna/android-*/**"
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
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
    implementation(libs.spark.core)
    implementation(libs.car.ui.lib)
    implementation(libs.gson)
    // BouncyCastle removed - causes System.exit(100) crash on Android due to Uptime access
    // implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    // implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation(libs.viewpager2)
    implementation(libs.runner)
    implementation(libs.swiperefreshlayout)

    // WebSocket for relay
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler) // <-- was implementation(...)

    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.fragment.ktx)

    // Cactus AI SDK - NOTE: Has known issues with Ktor on Android
    // The SDK uses JVM-specific Ktor APIs that may not work perfectly on Android
    implementation("com.cactuscompute:cactus:1.0.1-beta")

    debugImplementation(libs.stetho)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.robolectric)
    testImplementation("org.json:json:20230227") // For unit tests with JSON
    androidTestImplementation(libs.espresso.core)

    // implementation("com.github.tcheeric:nostr-java:main-SNAPSHOT")
}
