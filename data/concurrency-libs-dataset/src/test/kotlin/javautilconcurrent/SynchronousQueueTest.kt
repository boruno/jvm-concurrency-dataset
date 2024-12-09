@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class SynchronousQueueTest {
    private val queue = SynchronousQueue<Int>()

    @Operation
    fun add(e: Int) = queue.put(e)

    @Operation
    fun remove() = queue.take()

    @Operation
    fun offer(e: Int) = queue.offer(e)

    @Operation
    fun poll() = queue.poll()

    @Operation
    fun isEmpty() = queue.isEmpty()

    @Operation
    fun size() = queue.size

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}

