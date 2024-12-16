@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*

class AtomicLongArrayTest {
    private val atomicArray = AtomicLongArray(100)

    @Operation
    fun set(index: Int, newValue: Long) = atomicArray.set(index, newValue)

    @Operation
    fun get(index: Int) = atomicArray.get(index)

    @Operation
    fun getAndIncrement(index: Int) = atomicArray.getAndIncrement(index)

    @Operation
    fun getAndDecrement(index: Int) = atomicArray.getAndDecrement(index)

    @Operation
    fun incrementAndGet(index: Int) = atomicArray.incrementAndGet(index)

    @Operation
    fun decrementAndGet(index: Int) = atomicArray.decrementAndGet(index)

    @Operation
    fun compareAndSet(index: Int, expect: Long, update: Long) = atomicArray.compareAndSet(index, expect, update)

    @Test
    fun atomicLongArrayStressTest() = StressOptions().check(this::class)

    @Test
    fun atomicLongArrayModelCheckingTest() = ModelCheckingOptions().check(this::class)
}

