import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use(::load)
}

android {
    namespace = "com.bbkdownload.app"
    compileSdk = 35
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.bbkdownload.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.4"

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    signingConfigs {
        create("release") {
            if (signingPropertiesFile.isFile) {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isDebuggable = false
            // youtubedl-android 0.18.1 uses reflection while extracting its Python ZIP.
            // R8 class merging breaks Commons Compress before the downloader can start.
            isMinifyEnabled = false
            isShrinkResources = false
            if (signingPropertiesFile.isFile) signingConfig = signingConfigs.getByName("release")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val youtubedlAndroid = "0.18.1"

    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

val copyDebugApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/debug")) {
        include("app-debug.apk")
        rename { "BBKdownload-debug-arm64.apk" }
    }
    into(rootProject.layout.projectDirectory.dir("dist"))
}

val copyReleaseApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("app-release.apk")
        rename { "BBKdownload.apk" }
    }
    into(rootProject.layout.projectDirectory.dir("dist"))
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(copyReleaseApk)
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyDebugApk)
}
