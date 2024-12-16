@file:Suppress("HasPlatformType")

package org.jetbrains.research.agrona

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import org.agrona.concurrent.status.AtomicCounter
import org.agrona.concurrent.status.CountersManager
import org.agrona.concurrent.UnsafeBuffer
import java.nio.ByteBuffer

class AtomicCounterTest {
    private val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(8192))
    private val countersManager = CountersManager(buffer, buffer)
    private val counter = AtomicCounter(buffer, 0, countersManager)

    @Operation
    fun set(value: Long) = counter.set(value)

    @Operation
    fun get() = counter.get()

    @Operation
    fun increment() = counter.increment()

    @Operation
    fun decrement() = counter.decrement()

    @Operation
    fun sum() = counter.get()

    @Test
    fun atomicCounterStressTest() = StressOptions().check(this::class)

    @Test
    fun atomicCounterModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
