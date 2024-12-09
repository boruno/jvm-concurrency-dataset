@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*

class AtomicReferenceTest {
    private val atomicReference = AtomicReference<String>()

    @Operation
    fun set(newValue: String) = atomicReference.set(newValue)

    @Operation
    fun get() = atomicReference.get()

    @Operation
    fun compareAndSet(expect: String, update: String) = atomicReference.compareAndSet(expect, update)

    @Operation
    fun getAndSet(newValue: String) = atomicReference.getAndSet(newValue)

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}

