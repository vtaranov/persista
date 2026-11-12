plugins {
    id("com.android.application")
}

// Framework-free state checks run with the Kotlin compiler/runtime already supplied by AGP.
tasks.register<JavaExec>("checkAlarmState") {
    group = "verification"
    description = "Checks snooze delivery, cancellation, generations, clock changes and scheduling failures"
    dependsOn("compileDebugUnitTestKotlin")
    mainClass.set("online.taranov.persista.AlarmStateChecks")
    classpath = files(tasks.named("compileDebugUnitTestKotlin"), tasks.named("compileDebugKotlin")) +
        configurations.getByName("debugUnitTestRuntimeClasspath")
}

android {
    namespace = "online.taranov.persista"
    compileSdk = 37

    defaultConfig {
        applicationId = "online.taranov.persista"
        minSdk = 31
        targetSdk = 37
        versionCode = 10
        versionName = "0.9.0"
    }
}
