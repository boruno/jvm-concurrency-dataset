@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class ThreadPerTaskExecutorTest {
    private val executor = Executor { command -> Thread(command).start() }

    @Operation
    fun execute(task: Runnable) = executor.execute(task)

    @Ignore("Lincheck currently can't handle dynamic thread creation")
    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Ignore("Lincheck currently can't handle dynamic thread creation")
    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
