import java.io.File
import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val clapAssetsDirectory = layout.buildDirectory.dir("generated/clapAssets")

android {
    namespace = "digital.anchorstudio.soundprint"
    compileSdk = 36

    defaultConfig {
        applicationId = "digital.anchorstudio.soundprint"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    androidResources {
        noCompress += "onnx"
    }

    sourceSets {
        getByName("main").assets.srcDir(clapAssetsDirectory)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}


// The complete quantized CLAP model is fetched during the build and packaged
// into the APK. The installed app never downloads model weights.
val prepareBundledClapAssets by tasks.registering {
    outputs.dir(clapAssetsDirectory)

    doLast {
        val root = clapAssetsDirectory.get().asFile
        val baseUrl = "https://huggingface.co/Xenova/larger_clap_music_and_speech/resolve/56bcfdb/"
        val assets = listOf(
            Triple("onnx/audio_model_quantized.onnx", "021dd4fb962b4ed20cc3a6730b09e0ccf9f9c49931047032118a3b64828513a6", 78_155_433L),
            Triple("onnx/text_model_quantized.onnx", "8f9f29c5f6adee917553d4b3a70729c731c0d18b88efca0ae67c1a1fc278f3b6", 126_000_000L),
            Triple("vocab.json", "", 1L),
            Triple("merges.txt", "", 1L)
        )

        assets.forEach { (relativePath, expectedSha, minimumBytes) ->
            val destination = File(root, "clap/$relativePath")
            fun digest(file: java.io.File): String {
                val sha = MessageDigest.getInstance("SHA-256")
                file.inputStream().buffered(1024 * 1024).use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        sha.update(buffer, 0, count)
                    }
                }
                return sha.digest().joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
            }
            val valid = destination.isFile && destination.length() >= minimumBytes &&
                (expectedSha.isEmpty() || digest(destination) == expectedSha)
            if (!valid) {
                destination.parentFile.mkdirs()
                val temporary = File(destination.parentFile, destination.name + ".part")
                temporary.delete()
                println("Bundling CLAP asset: $relativePath")
                val connection = URL(baseUrl + relativePath).openConnection().apply {
                    connectTimeout = 20_000
                    readTimeout = 180_000
                    setRequestProperty("User-Agent", "Soundprint-Android-Build")
                }
                connection.getInputStream().use { input ->
                    temporary.outputStream().buffered(1024 * 1024).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                check(temporary.length() >= minimumBytes) { "Bundled CLAP asset is incomplete: $relativePath" }
                if (expectedSha.isNotEmpty()) {
                    val actualSha = digest(temporary)
                    check(actualSha == expectedSha) { "CLAP asset SHA-256 mismatch: $relativePath (expected $expectedSha, got $actualSha)" }
                }
                check(temporary.renameTo(destination)) { "Could not place CLAP asset: $relativePath" }
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareBundledClapAssets)
}
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(prepareBundledClapAssets)
    }
}
