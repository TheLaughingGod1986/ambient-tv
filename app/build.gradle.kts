plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "org.ambienttv.home"
 compileSdk = 35
 defaultConfig { applicationId = "org.ambienttv.home"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = "0.1.0"; testInstrumentationRunner = "org.ambienttv.home.DemoScreenshots" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":api")); testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303") }
