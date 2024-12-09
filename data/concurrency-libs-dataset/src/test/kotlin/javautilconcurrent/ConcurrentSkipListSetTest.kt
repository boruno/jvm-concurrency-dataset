@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class ConcurrentSkipListSetTest {
    private val set = ConcurrentSkipListSet<Int>()

    @Operation
    fun add(e: Int) = set.add(e)

    @Operation
    fun remove(e: Int) = set.remove(e)

    @Operation
    fun contains(e: Int) = set.contains(e)

    @Operation
    fun isEmpty() = set.isEmpty()

//    Size method is not linearizable
//    @Operation
//    fun size() = set.size

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
