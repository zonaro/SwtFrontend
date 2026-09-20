package com.swordfish.lemuroid.lib.injection

import androidx.work.ListenableWorker

/**
 * Dagger-based worker injection removed during vendoring.
 * The original used HasWorkerInjector interface with dagger.android.AndroidInjector.
 * Now uses direct construction via ServiceLocator.
 */
object AndroidWorkerInjection {
    fun inject(worker: ListenableWorker) {
        checkNotNull(worker) { "worker" }
        val application = worker.applicationContext
        if (application !is HasWorkerInjector) {
            throw RuntimeException(
                "${application.javaClass.canonicalName} does not " +
                    "implement ${HasWorkerInjector::class.java.canonicalName}",
            )
        }
        (application as HasWorkerInjector).injectWorker(worker)
    }
}
