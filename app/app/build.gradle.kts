plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.alertnet.bordersentinelalert"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alertnet.bordersentinelalert"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // SECURITY: Disable debugging in production
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

// Fix for AGP 8.x incremental packager bug:
tasks.configureEach {
    if (name.startsWith("package") && (name.endsWith("Debug") || name.endsWith("Release"))) {
        val isDebug = name.endsWith("Debug")
        val buildType = if (isDebug) "debug" else "release"
        
        doFirst {
            val apkDir = layout.buildDirectory.dir("outputs/apk").get().asFile
            val intermediatesApk = layout.buildDirectory.dir("intermediates/apk").get().asFile
            val incrementalDir = layout.buildDirectory.dir("intermediates/incremental").get().asFile
            
            println("Force cleaning APK directories before packaging: $name")
            if (apkDir.exists()) apkDir.deleteRecursively()
            if (intermediatesApk.exists()) intermediatesApk.deleteRecursively()
            
            // Also clean specific incremental directories if they exist
            val packagingDir = File(incrementalDir, "package${name.substringAfter("package")}")
            if (packagingDir.exists()) packagingDir.deleteRecursively()
        }
        
        doLast {
            val packagedApk = File(layout.buildDirectory.dir("outputs/apk/$buildType").get().asFile, "app-$buildType.apk")
            val targetDir = layout.buildDirectory.dir("intermediates/apk/$buildType").get().asFile
            if (packagedApk.exists()) {
                targetDir.mkdirs()
                val targetApk = File(targetDir, "app-$buildType.apk")
                packagedApk.copyTo(targetApk, overwrite = true)
                println("Copied packaged APK to intermediates: ${targetApk.absolutePath}")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Google Maps
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // Security & Biometrics
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Logging
    implementation(libs.timber)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}