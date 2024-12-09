@file:Suppress("HasPlatformType")

package guava

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import com.google.common.util.concurrent.*

class AtomicLongMapTest {
    private val atomicLongMap = AtomicLongMap.create<String>()

    @Operation
    fun put(key: String, newValue: Long) = atomicLongMap.put(key, newValue)

    @Operation
    fun get(key: String) = atomicLongMap.get(key)

    @Operation
    fun incrementAndGet(key: String) = atomicLongMap.incrementAndGet(key)

    @Operation
    fun decrementAndGet(key: String) = atomicLongMap.decrementAndGet(key)

    @Operation
    fun addAndGet(key: String, delta: Long) = atomicLongMap.addAndGet(key, delta)

    @Operation
    fun remove(key: String) = atomicLongMap.remove(key)

    @Operation
    fun size() = atomicLongMap.size()

    @Operation
    fun isEmpty() = atomicLongMap.isEmpty

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
