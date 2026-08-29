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

val syncPocketQaSourceCorpus by tasks.registering(Copy::class) {
    from("../bug_app/lib") {
        include(
            "ui/screens/catalog_screen.dart",
            "ui/screens/cart_screen.dart",
            "ui/screens/checkout_screen.dart",
            "state/cart_provider.dart",
            "ui/widgets/cart_item_tile.dart",
        )
    }
    into(layout.buildDirectory.dir("generated/pocketqaSourceCorpus/assets/sources"))
}

android.sourceSets.getByName("main").assets.srcDir(
    layout.buildDirectory.dir("generated/pocketqaSourceCorpus/assets").get().asFile,
)

tasks.named("preBuild").configure { dependsOn(syncPocketQaSourceCorpus) }

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r")
    testImplementation("junit:junit:4.13.2")
}
