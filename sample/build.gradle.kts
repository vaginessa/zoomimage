import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.navigation.safeargs.kotlin")
}

kotlin {
    applyMyHierarchyTemplate()

    androidTarget()

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(projects.zoomimageCompose)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.preview)
            api(compose.uiTooling.replace("ui-tooling", "ui-util"))
            api(libs.telephoto)
            api(libs.panpf.tools4j)
        }
        androidMain.dependencies {
            api(libs.panpf.sketch3.compose)

            implementation(projects.zoomimageComposeCoil)
            implementation(projects.zoomimageComposeGlide)
            implementation(projects.zoomimageComposeSketch)
            implementation(projects.zoomimageViewCoil)
            implementation(projects.zoomimageViewGlide)
            implementation(projects.zoomimageViewPicasso)
            implementation(projects.zoomimageViewSketch)
            implementation(projects.internal.images)
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.multidex)
            implementation(libs.androidx.navigation.fragment)
            implementation(libs.androidx.navigation.ui)
            implementation(libs.androidx.swiperefreshlayout)
            implementation(libs.google.material)
            implementation(libs.panpf.assemblyadapter4)
            implementation(libs.panpf.tools4a)
            implementation(libs.panpf.tools4j)
            implementation(libs.panpf.tools4k)
            implementation(libs.panpf.sketch3.extensions.view)
            implementation(libs.panpf.sketch3.extensions.compose)
            implementation(libs.subsamplingscaleimageview)
            implementation(libs.photoview)
            implementation(libs.androidx.constraintlayout.compose)

            /* compose */
            implementation(libs.androidx.compose.animation)
            implementation(libs.androidx.compose.foundation)
            implementation(libs.androidx.compose.material)
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.ui.tooling.preview)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.paging.compose)
            implementation(libs.panpf.sketch3.compose)
            implementation(libs.telephoto.coil)
        }
        desktopMain.dependencies {
            implementation(projects.internal.images)
            implementation(compose.desktop.currentOs)
        }

        commonTest.dependencies {
            implementation(projects.internal.testUtils)
        }
        androidInstrumentedTest.dependencies {
            implementation(projects.internal.testUtils)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.github.panpf.zoomimage.sample.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = property("GROUP").toString()
            packageVersion = property("versionName").toString().let {
                if (it.contains("-")) {
                    it.substring(0, it.indexOf("-"))
                } else {
                    it
                }
            }
        }
    }
}

androidApplication(nameSpace = "com.github.panpf.zoomimage.sample") {
    defaultConfig {
        buildConfigField("String", "VERSION_NAME", "\"${property("versionName").toString()}\"")
        buildConfigField("int", "VERSION_CODE", property("versionCode").toString())
    }
    signingConfigs {
        create("sample") {
            storeFile = project.file("sample.keystore")
            storePassword = "B027HHiiqKOMYesQ"
            keyAlias = "panpf-sample"
            keyPassword = "B027HHiiqKOMYesQ"
        }
    }
    buildTypes {
        debug {
            multiDexEnabled = true
            signingConfig = signingConfigs.getByName("sample")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("sample")
        }
    }

    flavorDimensions.add("default")

    androidResources {
        noCompress.add("bmp")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this
            if (output is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                output.outputFileName =
                    "zoomimage-sample-${variant.name}-${variant.versionName}.apk"
            }
        }
    }

    dependencies {
        debugImplementation(libs.leakcanary)
        debugImplementation(libs.androidx.compose.ui.tooling)
        debugImplementation(libs.androidx.compose.ui.test.manifest)
    }
}