plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.samfont"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.samfont"
        minSdk = 26
        targetSdk = 36
        versionCode = 10302
        versionName = "1.3.2"
    }

    flavorDimensions += "installMode"

    productFlavors {
        create("normal") {
            dimension = "installMode"
            buildConfigField("String", "INSTALL_MODE", "\"normal\"")
        }
        create("system") {
            dimension = "installMode"
            buildConfigField("String", "INSTALL_MODE", "\"system\"")
        }
    }

    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/samfontTemplateAssets"))
        }
    }
}

val copyFontTemplateApk by tasks.registering(Copy::class) {
    dependsOn(":fonttemplate:assembleDebug")
    from(project(":fonttemplate").layout.buildDirectory.file("outputs/apk/debug/fonttemplate-debug.apk"))
    into(layout.buildDirectory.dir("generated/samfontTemplateAssets/templates"))
    rename { "samsung-font-template.apk" }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyFontTemplateApk)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.android.tools.build:apksig:8.13.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
