import com.android.build.api.dsl.androidLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    alias(libs.plugins.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

val rustProjectDir = File(rootDir, "text_engine")
val libName = "text_engine"
val jniLibsDir = File(projectDir, "src/androidMain/jniLibs")
val execOps = project.serviceOf<ExecOperations>()

kotlin {
    androidLibrary {
        namespace = "com.mocharealm.accompanist.lyrics.ui"
        compileSdk = 36

        minSdk = 29

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }
        
        withJava()
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilations.configureEach {
            compilerOptions.configure {
                jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
            }
        }
    }
    jvm()

    val appleTargets = listOf(
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64()
    )
    appleTargets.forEach { target ->
        target.compilations.getByName("main") {
            val myInterop by cinterops.creating {
                defFile(project.file("src/nativeInterop/cinterop/$libName.def"))
                packageName = "com.mocharealm.accompanist.lyrics.ui.native"
            }
        }

        // 告诉链接器去哪里找编译好的 .a 文件
        target.binaries.all {
            val rustTarget = when (target.name) {
                "iosArm64" -> "aarch64-apple-ios"
                "iosSimulatorArm64" -> "aarch64-apple-ios-sim"
                "macosArm64" -> "aarch64-apple-darwin"
                else -> ""
            }
            if (rustTarget.isNotEmpty()) {
                linkerOpts("-L${rustProjectDir.absolutePath}/target/$rustTarget/release", "-l$libName")
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.uiToolingPreview)

                implementation(libs.gaze.capsule)

                implementation(libs.accompanist.lyrics.core)

                implementation(compose.components.resources)
            }
        }
        androidMain.dependencies {
        }
    }
}

publishing {
    repositories {
        maven {
            name = "local"
            url = uri("file:///E:/maven")
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = true
        )
    )

    signAllPublications()

    coordinates("com.mocharealm.accompanist", "lyrics-ui", rootProject.version.toString())

    pom {
        name = "Accompanist Lyrics UI"
        description = "A lyrics displaying library for Compose Multiplatform"
        inceptionYear = "2025"
        url = "https://mocharealm.com/open-source"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "6xingyv"
                name = "Simon Scholz"
                url = "https://github.com/6xingyv"
            }
        }
        scm {
            url = "https://github.com/6xingyv/Accompanist"
            connection = "scm:git:git://github.com/6xingyv/Accompanist.git"
            developerConnection = "scm:git:ssh://git@github.com/6xingyv/Accompanist.git"
        }
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-compiler-config.conf"))
}

// Android
val buildRustAndroid = tasks.register("buildRustAndroid") {
    doLast {
        val abiMap = mapOf(
            "arm64-v8a" to "aarch64-linux-android",
            "x86_64" to "x86_64-linux-android",
            "armeabi-v7a" to "armv7-linux-androideabi"
        )
        abiMap.forEach { (abi, target) ->
            // 显式调用 project.exec
            execOps.exec {
                workingDir = rustProjectDir
                commandLine("cargo", "ndk", "-t", abi, "build", "--release")
            }.assertNormalExitValue()

            copy {
                from("${rustProjectDir.absolutePath}/target/$target/release/lib$libName.so")
                into("${projectDir}/src/androidMain/jniLibs/$abi")
            }
        }
    }
}

// Apple 编译任务
val buildRustApple = tasks.register("buildRustApple") {
    doLast {
        val appleTargets = listOf(
            "aarch64-apple-ios",
            "aarch64-apple-ios-sim",
            "aarch64-apple-darwin"
        )
        appleTargets.forEach { target ->
            execOps.exec {
                workingDir = rustProjectDir
                commandLine("cargo", "build", "--target", target, "--release")
            }.assertNormalExitValue()
        }

        // 确保目录存在
        val interopDir = File(projectDir, "src/nativeInterop/cinterop")
        if (!interopDir.exists()) interopDir.mkdirs()

        execOps.exec {
            workingDir = rustProjectDir
            commandLine("cbindgen", "--config", "cbindgen.toml", "--crate", libName, "--output", "${interopDir.absolutePath}/$libName.h")
        }.assertNormalExitValue()
    }
}

val buildRustJvm = tasks.register("buildRustJvm") {
    group = "rust"
    doLast {
        // 根据当前机器系统编译
        execOps.exec {
            workingDir = rustProjectDir
            commandLine("cargo", "build", "--release")
        }

        val osName = System.getProperty("os.name").lowercase()
        val ext = when {
            osName.contains("win") -> "dll"
            osName.contains("mac") -> "dylib"
            else -> "so"
        }

        // 将库拷贝到 jvmMain 的资源目录中
        copy {
            from("${rustProjectDir.absolutePath}/target/release/lib$libName.$ext")
            into("${projectDir}/src/jvmMain/resources/natives")
        }
    }
}

// 确保在处理资源前先编译 Rust
tasks.named("jvmProcessResources") {
    dependsOn(buildRustJvm)
}