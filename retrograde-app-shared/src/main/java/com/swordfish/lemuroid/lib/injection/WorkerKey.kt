package com.swordfish.lemuroid.lib.injection

import androidx.work.ListenableWorker
import kotlin.reflect.KClass

/**
 * Dagger @MapKey annotation removed during vendoring.
 * The original used @MapKey to create a multibinding map of workers.
 * This class is kept as a marker for the worker factory map key.
 */
annotation class WorkerKey(val value: KClass<out ListenableWorker>)
