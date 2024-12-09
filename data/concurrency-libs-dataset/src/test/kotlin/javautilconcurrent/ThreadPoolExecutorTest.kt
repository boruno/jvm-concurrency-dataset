@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class ThreadPoolExecutorTest {
    private val executor = ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>())

    @Operation
    fun submit(task: Callable<Int>) = executor.submit(task)

    @Operation
    fun execute(task: Runnable) = executor.execute(task)

    @Operation
    fun shutdown() = executor.shutdown()

    @Operation
    fun isShutdown() = executor.isShutdown

    @Operation
    fun isTerminated() = executor.isTerminated

    @Ignore("Lincheck currently can't handle dynamic thread creation")
    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Ignore("Lincheck currently can't handle dynamic thread creation")
    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
