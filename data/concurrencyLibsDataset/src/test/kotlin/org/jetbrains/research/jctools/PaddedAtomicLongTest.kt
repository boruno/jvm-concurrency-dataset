@file:Suppress("HasPlatformType")

package org.jetbrains.research.jctools

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import org.jctools.util.PaddedAtomicLong

class PaddedAtomicLongTest {
    private val paddedAtomicLong = PaddedAtomicLong()

    @Operation
    fun set(newValue: Long) = paddedAtomicLong.set(newValue)

    @Operation
    fun get() = paddedAtomicLong.get()

    @Operation
    fun compareAndSet(expect: Long, update: Long) = paddedAtomicLong.compareAndSet(expect, update)

    @Operation
    fun getAndSet(newValue: Long) = paddedAtomicLong.getAndSet(newValue)

    @Operation
    fun incrementAndGet() = paddedAtomicLong.incrementAndGet()

    @Operation
    fun decrementAndGet() = paddedAtomicLong.decrementAndGet()

    @Operation
    fun addAndGet(delta: Long) = paddedAtomicLong.addAndGet(delta)

    @Test
    fun paddedAtomicLongStressTest() = StressOptions().check(this::class)

    @Test
    fun paddedAtomicLongModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
