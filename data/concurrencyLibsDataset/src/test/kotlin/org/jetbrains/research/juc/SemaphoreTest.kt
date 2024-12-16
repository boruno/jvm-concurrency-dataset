@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class SemaphoreTest {
    private val semaphore = Semaphore(1)

    @Operation
    fun acquire() = semaphore.acquire()

    @Operation
    fun release() = semaphore.release()

    @Operation
    fun tryAcquire() = semaphore.tryAcquire()

    @Operation
    fun getAvailablePermits() = semaphore.availablePermits()

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun semaphoreStressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun semaphoreModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
