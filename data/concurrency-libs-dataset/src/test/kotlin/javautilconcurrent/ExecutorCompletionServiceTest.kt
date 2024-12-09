@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class ExecutorCompletionServiceTest {
    private val executor = Executors.newFixedThreadPool(2)
    private val completionService = ExecutorCompletionService<Int>(executor)

    @Operation
    fun submit(task: Callable<Int>) = completionService.submit(task)

    @Operation
    fun take() = completionService.take()

    @Operation
    fun poll() = completionService.poll()

    @Operation
    fun pollWithTimeout(timeout: Long, unit: TimeUnit) = completionService.poll(timeout, unit)

    @Ignore("Lincheck currently can't handle dynamic thread creation")
    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Ignore("Lincheck currently can't handle dynamic thread creation")
    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
