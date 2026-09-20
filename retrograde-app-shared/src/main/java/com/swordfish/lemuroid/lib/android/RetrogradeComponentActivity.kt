package com.swordfish.lemuroid.lib.android

import androidx.activity.ComponentActivity

/**
 * Base ComponentActivity for Lemuroid. Dagger AndroidInjection removed during vendoring.
 * Use manual dependency injection or ServiceLocator pattern instead.
 */
abstract class RetrogradeComponentActivity : ComponentActivity()
