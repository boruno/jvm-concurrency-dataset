@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*

class AtomicBooleanTest {
    private val atomicBoolean = AtomicBoolean()

    @Operation
    fun set(newValue: Boolean) = atomicBoolean.set(newValue)

    @Operation
    fun get() = atomicBoolean.get()

    @Operation
    fun compareAndSet(expect: Boolean, update: Boolean) = atomicBoolean.compareAndSet(expect, update)

    @Operation
    fun getAndSet(newValue: Boolean) = atomicBoolean.getAndSet(newValue)

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
