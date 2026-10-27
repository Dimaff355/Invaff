plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 компилирует Kotlin сам; плагин здесь только фиксирует версию KGP (должна совпадать с compose-плагином)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
