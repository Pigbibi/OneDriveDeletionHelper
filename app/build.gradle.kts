import java.util.Properties

plugins { id("com.android.application") }
val registration = Properties().apply {
    rootProject.file("config/microsoft.properties").inputStream().use { load(it) }
}
val microsoftClientId = providers.gradleProperty("photokeepClientId")
    .orElse(registration.getProperty("clientId", "")).get().trim().lowercase()
val validClientId = microsoftClientId.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) &&
    microsoftClientId != "00000000-0000-0000-0000-000000000000"
require(microsoftClientId.isEmpty() || validClientId) { "Invalid public Microsoft client ID; see docs/MICROSOFT-APP.md." }
android {
    namespace = "cn.lisiyi.photokeep"
    compileSdk = 37
    defaultConfig {
        applicationId = "cn.lisiyi.photokeep"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "MICROSOFT_CLIENT_ID", "\"$microsoftClientId\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES") }
    lint { disable += setOf("OldTargetApi", "GradleDependency", "NewerVersionAvailable") }
    val keyFile = providers.environmentVariable("PHOTOKEEP_KEYSTORE").orNull
    if (keyFile != null) {
        signingConfigs.create("distribution") {
            storeFile = file(keyFile)
            storePassword = providers.environmentVariable("PHOTOKEEP_STORE_PASSWORD").get()
            keyAlias = "photokeep"
            keyPassword = providers.environmentVariable("PHOTOKEEP_STORE_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("distribution")
    }
    buildTypes.getByName("release") { isMinifyEnabled = false; isDebuggable = false }
}
val verifyMicrosoftRegistration by tasks.registering {
    inputs.property("microsoftClientId", microsoftClientId)
    doLast {
        check(validClientId) { "Release requires PhotoKeep's registered Microsoft client ID; see docs/MICROSOFT-APP.md." }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyMicrosoftRegistration) }
dependencies {
    // Official Microsoft login handles browser authorization and its encrypted token cache.
    implementation("com.microsoft.identity.client:msal:8.4.2")
    // Android's persistent scheduler survives process restarts and observes network constraints.
    implementation("androidx.work:work-runtime:2.11.2")
    // Disable transport-level retries for DELETE; platform URLConnection has no equivalent control.
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
