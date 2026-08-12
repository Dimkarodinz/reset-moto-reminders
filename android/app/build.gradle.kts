plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.resetlight"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.resetlight"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.6.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "RESEARCH_BUILD", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "RESEARCH_BUILD", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val generatedProfiles = layout.buildDirectory.dir("generated/profileAssets")
val adapterMapSource = rootProject.file("../adapter-maps/vlinker-mc-android.adaptermap.yaml")
val adapterSchemaSource = rootProject.file("../adapter-maps/adaptermap.schema.json")
val ecuMapSource = rootProject.file("../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml")
val ecuSchemaSource = rootProject.file("../ecu-maps/ecumap.schema.json")
val dtcMapSource = rootProject.file("../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml")
val dtcSchemaSource = rootProject.file("../dtc-maps/dtcmap.schema.json")
val dtcTranslationSchemaSource = rootProject.file("../dtc-maps/dtctranslation.schema.json")
val dtcTranslationSources = listOf("es", "uk", "fr", "de").map { locale ->
    rootProject.file("../dtc-maps/triumph-tiger-900-gt-pro-2021.$locale.dtctranslation.yaml")
}

val generateProfileAssets = tasks.register<Sync>("generateProfileAssets") {
    inputs.files(
        adapterMapSource,
        adapterSchemaSource,
        ecuMapSource,
        ecuSchemaSource,
        dtcMapSource,
        dtcSchemaSource,
        dtcTranslationSchemaSource,
        *dtcTranslationSources.toTypedArray(),
    )
    from(
        adapterMapSource,
        adapterSchemaSource,
        ecuMapSource,
        ecuSchemaSource,
        dtcMapSource,
        dtcSchemaSource,
        dtcTranslationSchemaSource,
        *dtcTranslationSources.toTypedArray(),
    )
    into(generatedProfiles.map { it.dir("profiles") })
    doFirst {
        require(Regex("(?m)^schema_version:\\s*2\\s*$").containsMatchIn(adapterMapSource.readText())) {
            "Unsupported or missing adapter-map schema_version"
        }
        require(Regex("(?m)^schema_version:\\s*3\\s*$").containsMatchIn(ecuMapSource.readText())) {
            "Unsupported or missing ECU-map schema_version"
        }
        require(Regex("(?m)^schema_version:\\s*3\\s*$").containsMatchIn(dtcMapSource.readText())) {
            "Unsupported or missing DTC-map schema_version"
        }
        dtcTranslationSources.forEach { source ->
            require(Regex("(?m)^schema_version:\\s*1\\s*$").containsMatchIn(source.readText())) {
                "Unsupported or missing DTC-translation schema_version in ${source.name}"
            }
        }
        require(
            adapterSchemaSource.isFile &&
                ecuSchemaSource.isFile &&
                dtcSchemaSource.isFile &&
                dtcTranslationSchemaSource.isFile
        ) {
            "All profile JSON Schemas must exist before maps are packaged"
        }
    }
}

android.sourceSets["main"].assets.srcDir(generatedProfiles)
tasks.named("preBuild").configure { dependsOn(generateProfileAssets) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.yaml:snakeyaml:2.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.21")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3")
    testImplementation("com.networknt:json-schema-validator:1.5.6")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
