package dev.jimmytai.camera_view

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class CameraLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun doOnCreate() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun doOnStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun doOnResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun doOnPause() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun doOnStop() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun doOnDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}