/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2025 Sergei Solodovnikov
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

import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

configure<BaseExtension> {
    buildToolsVersion = libs.versions.android.buildtools.get()

    compileSdkVersion(libs.versions.android.sdk.compile.get().toInt())

    defaultConfig {
        minSdk = libs.versions.android.sdk.min.get().toInt()

        vectorDrawables.useSupportLibrary = true

        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.all {
        java.srcDir("src/$name/kotlin")
    }

    // needed to build tests
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lintOptions {
        // Translations can miss some strings especially after Weblate pull request
        // Lets consider this as a warning to finish 'lintRelease' Gradle task
        // Update defaultConfig.resConfigs to add new supported translations
        warning("MissingTranslation")
    }
}

configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    "coreLibraryDesugaring"(libs.androidx.java.desugar)

    "implementation"(libs.androidx.annotations)
    "implementation"(libs.androidx.core)

    "implementation"(libs.kotlinx.serialization.json)
    "implementation"(libs.kotlinx.coroutines.android)

    "implementation"(libs.koin.androidx.scope)

    "implementation"(libs.tinylog.api)
    "implementation"(libs.tinylog.impl)

    "testImplementation"(libs.androidx.test.junit)
    "testImplementation"(libs.kotlinx.coroutines.core)
    "testImplementation"(libs.kotlinx.coroutines.test)
    "testImplementation"(libs.kotlin.test.junit)

    "testImplementation"(libs.mockk)
    "testImplementation"(libs.kluent) {
        exclude("com.nhaarman.mockitokotlin2")
    }
    "testImplementation"(libs.koin.test) {
        exclude("org.mockito")
    }

    "androidTestImplementation"(libs.androidx.test.runner)
    "androidTestImplementation"(libs.androidx.test.junit)

    "androidTestImplementation"(libs.kotlin.test.junit)
    "androidTestImplementation"(libs.kotlinx.coroutines.test)

    "androidTestImplementation"(libs.faker)
    "androidTestImplementation"(libs.koin.test)
    "androidTestImplementation"(libs.kluent) {
        exclude("com.nhaarman.mockitokotlin2")
    }
}