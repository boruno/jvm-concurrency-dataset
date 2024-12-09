@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*

class AtomicIntegerArrayTest {
    private val atomicArray = AtomicIntegerArray(100)

    @Operation
    fun set(index: Int, newValue: Int) = atomicArray.set(index, newValue)

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
    fun compareAndSet(index: Int, expect: Int, update: Int) = atomicArray.compareAndSet(index, expect, update)

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}

