@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class ForkJoinPoolTest {
    private val pool = ForkJoinPool()

    @Operation
    fun submit(task: ForkJoinTask<Int>) = pool.submit(task)

    @Operation
    fun execute(task: ForkJoinTask<Int>) = pool.execute(task)

    @Operation
    fun shutdown() = pool.shutdown()

    @Operation
    fun isShutdown() = pool.isShutdown

    @Operation
    fun isTerminated() = pool.isTerminated

    @Ignore("ForkJoinPool is currently unsupported")
    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Ignore("ForkJoinPool is currently unsupported")
    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
