@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class CompletableFutureTest {
    private val future = CompletableFuture<Int>()

    @Operation
    fun complete(value: Int) = future.complete(value)

    @Operation
    fun get() = future.get()

    @Operation
    fun isDone() = future.isDone

    @Operation
    fun isCancelled() = future.isCancelled

    @Operation
    fun cancel(mayInterruptIfRunning: Boolean) = future.cancel(mayInterruptIfRunning)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun completableFutureStressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun completableFutureModelCheckingTest() = ModelCheckingOptions().check(this::class)
}

