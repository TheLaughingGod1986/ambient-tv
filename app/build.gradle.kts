plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "org.ambienttv.home"
 compileSdk = 35
 defaultConfig { applicationId = "org.ambienttv.home"; minSdk = 23; targetSdk = 35; versionCode = 2; versionName = "0.2.0"; testInstrumentationRunner = providers.gradleProperty("tvTestRunner").getOrElse("org.ambienttv.home.TvRegression") }
 val releaseStore = providers.environmentVariable("AMBIENT_KEYSTORE").orNull
 signingConfigs {
  if (releaseStore != null) create("distribution") {
   storeFile = file(releaseStore)
   storePassword = providers.environmentVariable("AMBIENT_STORE_PASSWORD").get()
   keyAlias = providers.environmentVariable("AMBIENT_KEY_ALIAS").get()
   keyPassword = providers.environmentVariable("AMBIENT_KEY_PASSWORD").get()
  }
 }
 buildTypes { getByName("release") { if (releaseStore != null) signingConfig = signingConfigs.getByName("distribution") } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":api")); testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303") }
