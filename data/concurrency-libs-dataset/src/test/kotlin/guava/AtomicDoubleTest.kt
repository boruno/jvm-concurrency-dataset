@file:Suppress("HasPlatformType")

package guava

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import com.google.common.util.concurrent.*

class AtomicDoubleTest {
    private val atomicDouble = AtomicDouble()

    @Operation
    fun set(newValue: Double) = atomicDouble.set(newValue)

    @Operation
    fun get() = atomicDouble.get()

    @Operation
    fun compareAndSet(expect: Double, update: Double) = atomicDouble.compareAndSet(expect, update)

    @Operation
    fun getAndSet(newValue: Double) = atomicDouble.getAndSet(newValue)

    @Operation
    fun addAndGet(delta: Double) = atomicDouble.addAndGet(delta)

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
