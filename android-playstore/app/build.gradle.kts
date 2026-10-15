import java.util.Properties

plugins { id("com.android.application") }

dependencies {
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.activity:activity:1.13.0")
    testImplementation("junit:junit:4.13.2")
}

// Modern Kotlin stdlib bundles the JDK 7/8 extensions; exclude legacy split jars
// that cause duplicate classes when combined with the current Play Billing library.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

val signingPropsFile = rootProject.file("keystore.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) signingPropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.munna.dc_calculator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.munna.dc_calculator"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.1"
    }

    signingConfigs {
        if (signingPropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile", "upload-keystore.jks"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias", "dc-calculator-upload")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingPropsFile.exists()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
