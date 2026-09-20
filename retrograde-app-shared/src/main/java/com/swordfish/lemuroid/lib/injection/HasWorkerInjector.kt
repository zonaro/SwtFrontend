package com.swordfish.lemuroid.lib.injection

import androidx.work.ListenableWorker

/**
 * Dagger AndroidInjector removed during vendoring.
 * The original interface used dagger.android.AndroidInjector<ListenableWorker>.
 * Simplified to a basic injection contract.
 */
interface HasWorkerInjector {
    fun injectWorker(worker: ListenableWorker)
}
