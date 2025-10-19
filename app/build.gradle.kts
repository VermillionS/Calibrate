plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.calibrate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.calibrate"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    // Chart (calibration curve)
    implementation(libs.mpandroidchart)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)


    //implementation(libs.androidx.room.common.jvm)
    //implementation(libs.roomruntime2) roomruntime2 = { group = "androidx.room", name = "room-compiler", version.ref = "roomRuntimeJvm" }

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.preference)
    //annotationProcessor 'androidx.room:room-compiler:2.6.1' // <-- processor only (Java project)
    annotationProcessor(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}