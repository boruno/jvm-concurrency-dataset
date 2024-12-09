@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*

class AtomicIntegerTest {
    private val atomicInteger = AtomicInteger()

    @Operation
    fun set(newValue: Int) = atomicInteger.set(newValue)

    @Operation
    fun get() = atomicInteger.get()

    @Operation
    fun getAndIncrement() = atomicInteger.getAndIncrement()

    @Operation
    fun getAndDecrement() = atomicInteger.getAndDecrement()

    @Operation
    fun incrementAndGet() = atomicInteger.incrementAndGet()

    @Operation
    fun decrementAndGet() = atomicInteger.decrementAndGet()

    @Operation
    fun compareAndSet(expect: Int, update: Int) = atomicInteger.compareAndSet(expect, update)

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
