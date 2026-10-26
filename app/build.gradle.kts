import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.vuegram"

    // Only the languages VueGram is translated into (AppLanguage.SUPPORTED): library strings in other languages would
    // mix with English on a TV set to a language VueGram doesn't speak yet.
    androidResources {
        localeFilters += listOf("en", "zh", "zh-rTW", "zh-rHK", "es", "pt", "ru", "ar", "iw", "hi", "in", "fa", "tr")
    }

    // One APK per processor type (about half the size of one with both), plus a universal APK for first installs
    // (vuegram.com/get). TDLib is the bulk of each: libtdjni.so for 32-bit and 64-bit ARM. Most TVs run 32-bit apps even
    // on 64-bit chips, so armeabi-v7a is the APK most of them get; the updater picks by Build.SUPPORTED_ABIS.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    // Lint must report zero errors; app/lint.xml lists the few checks that can't apply here, with reasons.
    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
    }
    compileSdk {
        version = release(36)
    }

    val properties = Properties().apply {
        val localProperties = rootProject.file("local.properties")
        if (localProperties.exists()) {
            localProperties.inputStream().use { stream ->
                load(stream)
            }
        }
    }

    // The one place the version is set. versionCode follows it (1.2.3 -> 10203), so the two can't drift and it only
    // goes up; Android refuses an update whose versionCode is not higher. Each part stays below 100.
    val appVersion = "0.1.0"
    val appVersionCode = appVersion.split(".").map { it.toInt() }.also { parts ->
        require(parts.size == 3 && parts.all { it in 0..99 }) { "appVersion must look like 1.2.3, each part below 100" }
    }.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

    // The release key lives outside the repository: ~/.vuegram/signing.properties (or the file VUEGRAM_SIGNING
    // names) with storeFile, storePassword, keyAlias and keyPassword. Every release must be signed with the same key,
    // or Android refuses it as an update. RELEASE_* in local.properties or the environment still work too.
    val signing = Properties().apply {
        val path = System.getenv("VUEGRAM_SIGNING")
            ?: "${System.getProperty("user.home")}/.vuegram/signing.properties"
        val file = File(path)
        if (file.exists()) file.inputStream().use { load(it) }
    }
    val releaseStoreFile = signing.getProperty("storeFile")
        ?: properties.getProperty("RELEASE_STORE_FILE")
        ?: System.getenv("RELEASE_STORE_FILE")
        ?: ""
    val releaseStorePassword = signing.getProperty("storePassword")
        ?: properties.getProperty("RELEASE_STORE_PASSWORD")
        ?: System.getenv("RELEASE_STORE_PASSWORD")
        ?: ""
    val releaseKeyAlias = signing.getProperty("keyAlias")
        ?: properties.getProperty("RELEASE_KEY_ALIAS")
        ?: System.getenv("RELEASE_KEY_ALIAS")
        ?: ""
    val releaseKeyPassword = signing.getProperty("keyPassword")
        ?: properties.getProperty("RELEASE_KEY_PASSWORD")
        ?: System.getenv("RELEASE_KEY_PASSWORD")
        ?: ""

    signingConfigs {
        create("release") {
            if (releaseStoreFile.isNotBlank()) {
                storeFile = file(releaseStoreFile)
            }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    defaultConfig {
        applicationId = "com.vuegram.tv"
        minSdk = 23
        targetSdk = 36
        versionName = appVersion
        versionCode = appVersionCode

        val apiId = properties.getProperty("TELEGRAM_API_ID") ?: ""
        val apiHash = properties.getProperty("TELEGRAM_API_HASH") ?: ""
        buildConfigField("String", "TELEGRAM_API_ID", "\"$apiId\"")
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$apiHash\"")
        // Where the in-app updater looks for new releases (owner/repo on GitHub).
        buildConfigField("String", "UPDATE_REPO", "\"vuegram/vuegram\"")

    }

    buildTypes {
        release {
            // Release builds never carry Telegram API keys, even if local.properties has them for development:
            // everyone who installs VueGram enters their own on first start.
            buildConfigField("String", "TELEGRAM_API_ID", "\"\"")
            buildConfigField("String", "TELEGRAM_API_HASH", "\"\"")
            isMinifyEnabled = false
            if (releaseStoreFile.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.leanback)
    implementation(libs.glide)
    implementation(libs.zxing.core)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    testImplementation(libs.junit)
}