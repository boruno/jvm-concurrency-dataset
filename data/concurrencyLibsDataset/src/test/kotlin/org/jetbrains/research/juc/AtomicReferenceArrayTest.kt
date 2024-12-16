@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*

class AtomicReferenceArrayTest {
    private val atomicArray = AtomicReferenceArray<String>(100)

    @Operation
    fun set(index: Int, newValue: String) = atomicArray.set(index, newValue)

    @Operation
    fun get(index: Int) = atomicArray.get(index)

    @Operation
    fun compareAndSet(index: Int, expect: String, update: String) = atomicArray.compareAndSet(index, expect, update)

    @Operation
    fun getAndSet(index: Int, newValue: String) = atomicArray.getAndSet(index, newValue)

    @Test
    fun atomicReferenceArrayStressTest() = StressOptions().check(this::class)

    @Test
    fun atomicReferenceArrayModelCheckingTest() = ModelCheckingOptions().check(this::class)
}

