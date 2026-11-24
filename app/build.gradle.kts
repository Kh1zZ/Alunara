import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps =
    Properties().apply {
        if (keystorePropsFile.isFile) {
            keystorePropsFile.inputStream().use(::load)
        }
    }

fun resolveSigningValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile =
    resolveSigningValue("storeFile", "ALUNARA_KEYSTORE_FILE")?.let(rootProject::file)
val releaseStorePassword = resolveSigningValue("storePassword", "ALUNARA_KEYSTORE_PASSWORD")
val releaseKeyAlias = resolveSigningValue("keyAlias", "ALUNARA_KEY_ALIAS")
val releaseKeyPassword = resolveSigningValue("keyPassword", "ALUNARA_KEY_PASSWORD")
val hasReleaseKeystore =
    releaseStoreFile?.isFile == true &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

fun deriveSemVerCode(versionName: String): Int {
    val parts = versionName.substringBefore('-').split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return ((major * 10_000) + (minor * 100) + patch).coerceAtLeast(1)
}

val resolvedVersionName =
    providers.gradleProperty("versionName").orNull?.takeIf { it.isNotBlank() } ?: "0.2.3"
val resolvedVersionCode =
    providers.gradleProperty("versionCode").orNull?.toIntOrNull()?.coerceAtLeast(1)
        ?: deriveSemVerCode(resolvedVersionName)

android {
    namespace = "com.alunara.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alunara.app"
        minSdk = 28
        targetSdk = 35
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (hasReleaseKeystore) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes +=
                listOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "META-INF/INDEX.LIST",
                    "META-INF/io.netty.versions.properties",
                    "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                    "META-INF/NOTICE.md",
                    "META-INF/CONTRIBUTORS.md",
                    "META-INF/LICENSE.md",
                    "META-INF/services/javax.script.ScriptEngineFactory",
                )
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        // Alunara targets Leanback ARM Android TV hardware; release excludes x86/x86_64 binaries intentionally.
        disable += "ChromeOsAbiSupport"
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:music"))
    implementation(project(":core:lyrics"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.androidx.room.runtime)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
