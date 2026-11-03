import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
val signing = Properties().apply {
    val file = rootProject.file("local/signing.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
android {
    namespace = "io.github.jazzybeen.androiddynamicisland"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.jazzybeen.androiddynamicisland"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1"
    }
    if (signing.isNotEmpty()) signingConfigs.create("localRelease") {
        storeFile = rootProject.file(signing.getProperty("storeFile"))
        storePassword = signing.getProperty("storePassword")
        keyAlias = signing.getProperty("keyAlias")
        keyPassword = signing.getProperty("keyPassword")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
            if (signing.isNotEmpty()) signingConfig = signingConfigs.getByName("localRelease")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    lint { abortOnError = true }
}
dependencies {
    implementation(files(rootProject.file("local/libbox.aar")))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation:1.9.5")
    implementation("androidx.compose.ui:ui:1.9.5")
    implementation("androidx.compose.material3:material3:1.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
