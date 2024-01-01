plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
    signing
}

android {
    namespace = "io.appflags.android"
    compileSdk = 33

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "SDK_VERSION", "\"$version\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.appflags"
            artifactId = "appflags-sdk-android"
            version = version

            afterEvaluate { from(components["release"]) }

            pom {
                name.set("AppFlags Android SDK")
                description.set("Android SDK for AppFlags")
                url.set("https://appflags.io/")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://www.opensource.org/licenses/mit-license.php")
                    }
                }
                developers {
                    developer {
                        name.set("AppFlags")
                        email.set("support@appflags.io")
                    }
                }
                scm {
                    connection.set("scm:git:https://gitlab.com/app-flags/sdks/appflags-sdk-android.git")
                    developerConnection.set("scm:git:https://gitlab.com/app-flags/sdks/appflags-sdk-android.git")
                    url.set("https://appflags.io/")
                }
            }

        }
    }
}

signing {
    val signingKeyId = System.getenv("GPG_KEY_NAME")?.takeLast(8) // signing plugin wants last 8 chars of key name
    val signingKey = System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = System.getenv("GPG_PASSPHRASE")
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    setRequired {
        // signing is only required for publishing to Maven Central
        gradle.taskGraph.allTasks.any { it.name == "publishToSonatype" }
    }
    sign(publishing.publications)
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.11.0")
    implementation("io.appflags:appflags-java-protobuf-types:1.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}