plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":third_party:core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.okhttp.dnsoverhttps)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
