# Sets up a PowerShell session to build Sortr with the local toolchain on E:.
# Usage (from the project folder):   . .\scripts\env.ps1
# Then build with:                   gradle :app:assembleDebug

$toolchain = "E:\android-toolchain"

$env:JAVA_HOME = "$toolchain\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "$toolchain\sdk"
$env:ANDROID_SDK_ROOT = "$toolchain\sdk"
# Keep Gradle's dependency cache off C:.
$env:GRADLE_USER_HOME = "$toolchain\gradle-home"
# Emulator images for Sortr live on E: too.
$env:ANDROID_AVD_HOME = "$toolchain\avd"

$env:Path = @(
    "$env:JAVA_HOME\bin",
    "$toolchain\gradle-8.10.2\bin",
    "$env:ANDROID_HOME\platform-tools",
    "$env:ANDROID_HOME\emulator",
    "$env:ANDROID_HOME\cmdline-tools\latest\bin",
    $env:Path
) -join ";"
