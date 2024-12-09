@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class FutureTaskTest {
    private val task = FutureTask(Callable { 42 })

    @Operation
    fun run() = task.run()

    @Operation
    fun get() = task.get()

    @Operation
    fun isDone() = task.isDone

    @Operation
    fun isCancelled() = task.isCancelled

    @Operation
    fun cancel(mayInterruptIfRunning: Boolean) = task.cancel(mayInterruptIfRunning)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
