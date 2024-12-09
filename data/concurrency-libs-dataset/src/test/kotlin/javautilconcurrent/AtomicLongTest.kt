@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*

class AtomicLongTest {
    private val atomicLong = AtomicLong()

    @Operation
    fun set(newValue: Long) = atomicLong.set(newValue)

    @Operation
    fun get() = atomicLong.get()

    @Operation
    fun getAndIncrement() = atomicLong.getAndIncrement()

    @Operation
    fun getAndDecrement() = atomicLong.getAndDecrement()

    @Operation
    fun incrementAndGet() = atomicLong.incrementAndGet()

    @Operation
    fun decrementAndGet() = atomicLong.decrementAndGet()

    @Operation
    fun compareAndSet(expect: Long, update: Long) = atomicLong.compareAndSet(expect, update)

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}

