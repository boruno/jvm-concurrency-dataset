@file:Suppress("HasPlatformType")

package org.jetbrains.research.guava

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import com.google.common.util.concurrent.*

class AtomicDoubleArrayTest {
    private val atomicArray = AtomicDoubleArray(100)

    @Operation
    fun set(index: Int, newValue: Double) = atomicArray.set(index, newValue)

    @Operation
    fun get(index: Int) = atomicArray.get(index)

    @Operation
    fun compareAndSet(index: Int, expect: Double, update: Double) = atomicArray.compareAndSet(index, expect, update)

    @Operation
    fun getAndSet(index: Int, newValue: Double) = atomicArray.getAndSet(index, newValue)

    @Operation
    fun addAndGet(index: Int, delta: Double) = atomicArray.addAndGet(index, delta)

    @Test
    fun atomicDoubleArrayStressTest() = StressOptions().check(this::class)

    @Test
    fun atomicDoubleArrayModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
