plugins {
    id("com.android.application")
}

android {
    namespace = "com.gjdnd.aicollector"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gjdnd.aicollector"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.security:security-crypto:1.0.0")
}
