@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class CyclicBarrierTest {
    private val barrier = CyclicBarrier(2)

    @Operation
    fun await() = barrier.await()

    @Operation
    fun getNumberWaiting() = barrier.numberWaiting

    @Operation
    fun isBroken() = barrier.isBroken

    @Operation
    fun reset() = barrier.reset()

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
