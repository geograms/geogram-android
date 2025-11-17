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
        versionCode = 22
        versionName = "0.5.22"

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
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
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

    debugImplementation(libs.stetho)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.robolectric)
    testImplementation("org.json:json:20230227") // For unit tests with JSON
    androidTestImplementation(libs.espresso.core)

    // implementation("com.github.tcheeric:nostr-java:main-SNAPSHOT")
}
