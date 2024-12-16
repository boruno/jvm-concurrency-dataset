@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class ScheduledThreadPoolExecutorTest {
    private val executor = ScheduledThreadPoolExecutor(2)

    @Operation
    fun schedule(task: Runnable, delay: Long, unit: TimeUnit) = executor.schedule(task, delay, unit)

    @Operation
    fun scheduleAtFixedRate(task: Runnable, initialDelay: Long, period: Long, unit: TimeUnit) =
        executor.scheduleAtFixedRate(task, initialDelay, period, unit)

    @Operation
    fun scheduleWithFixedDelay(task: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit) =
        executor.scheduleWithFixedDelay(task, initialDelay, delay, unit)

    @Operation
    fun shutdown() = executor.shutdown()

    @Operation
    fun isShutdown() = executor.isShutdown

    @Operation
    fun isTerminated() = executor.isTerminated

    @Ignore("Lincheck currently can't handle dynamic thread creation")
    @Test
    fun scheduledThreadPoolExecutorStressTest() = StressOptions().check(this::class)

    @Ignore("Lincheck currently can't handle dynamic thread creation")
    @Test
    fun scheduledThreadPoolExecutorModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
