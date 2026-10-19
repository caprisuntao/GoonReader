// Top-level build file. Nothing is applied here; each module opts in.
// Kotlin itself is not applied as a plugin: AGP 9 compiles Kotlin natively
// (built-in Kotlin), which is why only compiler *extensions* (Compose, KSP, Hilt)
// appear below.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
