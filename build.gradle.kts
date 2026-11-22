// The owner's usual toolchain (as Phone-Relay's): AGP 9 compiles Kotlin itself ("built-in
// Kotlin"), so there is no kotlin-android plugin.
plugins {
    alias(libs.plugins.android.application) apply false
}
