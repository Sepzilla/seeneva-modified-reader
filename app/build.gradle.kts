/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2025 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>
 */

import com.android.build.api.dsl.SigningConfig
import com.android.build.api.dsl.VariantDimension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.BaseExtension
import extension.envOrProperty
import extension.loadProperties
import extension.requireEnvOrProperty
import extension.signProperties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("seeneva-base-configuration")
}

// True if this build is running in CI
val buildUsingCI = !System.getenv("CI").isNullOrEmpty()

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    splits {
        abi {
            isEnable = !hasProperty("seeneva.disableSplitApk")

            reset()

            include(*Abi.values().map { it.abiName }.toTypedArray())

            isUniversalApk = true
        }
    }

    signingConfigs {
        register("release") {
            applyPropertiesSigning()
        }
        named("debug") {
            if (buildUsingCI) {
                // Allow override signing properties on build started by CI
                applyPropertiesSigning()
            }
        }
    }

    defaultConfig {
        targetSdk = libs.versions.android.sdk.target.get().toInt()

        namespace = "app.seeneva.reader"

        applicationId = "app.seeneva.reader"
        // allow to set app id suffix from properties. It is required by CI.
        applicationIdSuffix =
            envOrProperty(extension.ENV_APP_ID_SUFFIX, extension.PROP_APP_ID_SUFFIX)

        enableShowDonate(true)

        missingDimensionStrategy(RustBuildTypeFlavor.NAME, RustBuildTypeFlavor.RUST_RELEASE)

        loadProperties(rootDir.resolve("seeneva.properties")).also { seenevaProperties ->
            versionCode = requireEnvOrProperty(
                extension.ENV_VERSION_CODE,
                extension.PROP_VERSION_CODE,
                seenevaProperties
            ).toInt()

            versionName = requireEnvOrProperty(
                extension.ENV_VERSION_NAME,
                extension.PROP_VERSION_NAME,
                seenevaProperties
            )
        }
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = switchableSigningConfig("release")

            ndk {
                // https://developer.android.com/studio/build/shrink-code.html#native-crash-support
                debugSymbolLevel = if (hasProperty(extension.PROP_NO_DEB_SYMBOLS)) {
                    "none"
                } else {
                    "full"
                }
            }
        }
        named("debug") {
            isMinifyEnabled = false
            isDebuggable = true

            applicationIdSuffix = ".debug"

            signingConfig = switchableSigningConfig("debug")
        }
    }
    flavorDimensions += listOf(AppStoreFlavor.NAME)

    productFlavors {
        register(AppStoreFlavor.GOOGLE_PLAY) {
            dimension = AppStoreFlavor.NAME

            enableShowDonate(false)
        }

        register(AppStoreFlavor.FDROID) {
            dimension = AppStoreFlavor.NAME

            versionNameSuffix = "-fdroid"
        }
        register(AppStoreFlavor.GITHUB) {
            dimension = AppStoreFlavor.NAME

            versionNameSuffix = "-gh"
        }
    }

    packaging {
        resources.excludes += setOf(
            // https://github.com/Kotlin/kotlinx.coroutines#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
            "DebugProbesKt.bin",
            // Not needed right now, but should return if I will use web connections
            "okhttp3/**/publicsuffixes.gz"
        )
    }

//    testOptions{
//        unitTests.setIncludeAndroidResources(true)
//    }
}

androidComponents {
    if (buildUsingCI) {
        onVariants(callback = ::configureOutputName)
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":logic"))
    implementation(project(":common"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.recyclerview.selection)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.java8)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.swiperefreshlayout)

    implementation(libs.google.material)

    implementation(libs.koin.androidx.viewmodel)
    implementation(libs.koin.androidx.workmanager)

    implementation(libs.scaleimageview)
}

/**
 * Allows conditionally disable output APK/AAB signing
 * @param name name of a sign config
 * @param enabled true if build signed output, unsigned otherwise
 */
fun BaseExtension.switchableSigningConfig(
    name: String,
    enabled: Boolean = !hasProperty(extension.PROP_BUILD_UNSIGNED)
) = if (enabled) {
    signingConfigs[name]
} else {
    null
}

/**
 * Apply signing params from the Java properties file or Gradle properties if properties file is not provided
 * @param propertiesFileName Java properties file which should be used
 */
fun SigningConfig.applyPropertiesSigning(propertiesFileName: String = "keystore.properties") {
    // Add `keystore.properties` to provide data needed for app signing process:
    // seeneva.storeFile=/path/to/keystore
    // seeneva.storePassword=
    // seeneva.keyAlias=
    // seeneva.keyPassword=

    val signProperties = signProperties(propertiesFileName) ?: return

    storeFile = file(signProperties[extension.PROP_STORE_FILE] as String).absoluteFile
    storePassword = signProperties[extension.PROP_STORE_PASS] as String
    keyAlias = signProperties[extension.PROP_KEY_ALIAS] as String
    keyPassword = signProperties[extension.PROP_KEY_PASS] as String
}

/**
 * Change is donate button enabled
 * @param enable true if donate button enabled
 */
fun VariantDimension.enableShowDonate(enable: Boolean) {
    buildConfigField("boolean", "DONATE_ENABLED", "$enable")
}

/**
 * Configure naming of output APKs
 * @param variant build variant to configure
 */
fun configureOutputName(variant: ApplicationVariant) {
    val abiSplitEnabled = android.splits.abi.isEnable

    variant.outputs
        .asSequence()
        .filterIsInstance<VariantOutputImpl>()
        .forEach { output ->
            output.outputFileName = buildString {
                append("seeneva-${output.versionName.get()}")

                if (abiSplitEnabled) {
                    val abiFilters = output.filters
                        .filter { it.filterType == FilterConfiguration.FilterType.ABI }
                        .toList()

                    append('-')
                    append(
                        if (abiFilters.isEmpty()) {
                            "universal"
                        } else {
                            abiFilters.joinToString("-") { it.identifier }
                        })
                }

                if (variant.debuggable) {
                    append("-debug")
                }

                if ((variant.signingConfig as? SigningConfigImpl)?.isSigningReady() == false) {
                    append("-unsigned")
                }

                append(".apk")
            }
        }
}