plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.resetlight.research.general"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.resetlight.research.general"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

val sharedMain = rootProject.file("app/src/main/java/dev/resetlight")
android.sourceSets["main"].java.srcDirs(
    "src/main/java",
    sharedMain.resolve("adapter"),
    sharedMain.resolve("diagnostics"),
    sharedMain.resolve("domain"),
    sharedMain.resolve("logging"),
    sharedMain.resolve("profiles"),
    sharedMain.resolve("transport"),
)

val generatedProfiles = layout.buildDirectory.dir("generated/profileAssets")
val generatedLauncherResources = layout.buildDirectory.dir("generated/sharedLauncherResources")
val sharedLauncherSource = rootProject.file("app/src/main/res")
val sharedLauncherFiles = fileTree(sharedLauncherSource) {
    include("mipmap-*/ic_launcher_foreground.png")
    include("values/colors.xml")
}
val profileSources = listOf(
    rootProject.file("../adapter-maps/vlinker-mc-android.adaptermap.yaml"),
    rootProject.file("../adapter-maps/adaptermap.schema.json"),
    project.file("profiles/standard-obd-read.researchprofile.yaml"),
)
val generateProfileAssets = tasks.register<Sync>("generateProfileAssets") {
    inputs.files(profileSources)
    from(profileSources)
    into(generatedProfiles.map { it.dir("profiles") })
    doFirst { require(profileSources.all { it.isFile }) }
}
val generateSharedLauncherResources = tasks.register<Sync>("generateSharedLauncherResources") {
    inputs.files(sharedLauncherFiles)
    from(sharedLauncherSource) {
        include("mipmap-*/ic_launcher_foreground.png")
        include("values/colors.xml")
    }
    into(generatedLauncherResources)
}
android.sourceSets["main"].assets.srcDir(generatedProfiles)
android.sourceSets["main"].res.srcDir(generatedLauncherResources)
android.sourceSets["test"].resources.srcDir(generatedProfiles)
tasks.named("preBuild").configure {
    dependsOn(generateProfileAssets, generateSharedLauncherResources)
}
tasks.matching { it.name.contains("UnitTest", ignoreCase = true) }.configureEach {
    dependsOn(generateProfileAssets)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.yaml:snakeyaml:2.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
