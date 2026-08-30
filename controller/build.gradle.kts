plugins {
    id("com.android.application")
}

android {
    namespace = "com.indium.pocketqa.controller"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        applicationId = "com.indium.pocketqa.controller"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

val demoSourceKeys = listOf(
    "providers/cart_provider.dart",
    "ui/screens/profile/edit_profile_screen.dart",
    "ui/screens/settings/delivery_preferences_screen.dart",
    "ui/screens/experimental/low_semantics_screen.dart",
    "ui/screens/category/category_screen.dart",
)

val generatedSourceCorpus = layout.buildDirectory.dir("generated/pocketqaSourceCorpus/assets/sources")

val syncPocketQaSourceCorpus by tasks.registering(Sync::class) {
    from("../bug_app/bugged/lib") {
        demoSourceKeys.forEach { include(it) }
    }
    into(generatedSourceCorpus)
}

val verifyPocketQaSourceCorpus by tasks.registering {
    dependsOn(syncPocketQaSourceCorpus)
    doLast {
        val sourceRoot = generatedSourceCorpus.get().asFile
        val missing = demoSourceKeys.filterNot { sourceRoot.resolve(it).isFile }
        check(missing.isEmpty()) {
            "PocketQA demo source corpus is incomplete: ${missing.joinToString()}"
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(
    layout.buildDirectory.dir("generated/pocketqaSourceCorpus/assets").get().asFile,
)

tasks.named("preBuild").configure { dependsOn(verifyPocketQaSourceCorpus) }

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r")
    testImplementation("junit:junit:4.13.2")
}
