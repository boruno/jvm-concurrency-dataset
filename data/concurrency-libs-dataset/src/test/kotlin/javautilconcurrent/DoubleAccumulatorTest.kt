@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*
import java.util.function.*

class DoubleAccumulatorTest {
    private val accumulator = DoubleAccumulator(DoubleBinaryOperator { x, y -> x + y }, 0.0)

    @Operation
    fun accumulate(value: Double) = accumulator.accumulate(value)

    @Operation
    fun get() = accumulator.get()

    @Operation
    fun reset() = accumulator.reset()

    @Operation
    fun getThenReset() = accumulator.getThenReset()

    @Test
    fun stressTest() = expectInvalidResults {
        StressOptions().check(this::class)
    }

    @Test
    fun modelCheckingTest() = expectInvalidResults {
        ModelCheckingOptions().check(this::class)
    }

    private fun expectInvalidResults(block: () -> Unit) {
        try {
            block()
        } catch (e: LincheckAssertionError) {
            println("LincheckAssertionError caught: ${e.message}")
        }
    }
}

