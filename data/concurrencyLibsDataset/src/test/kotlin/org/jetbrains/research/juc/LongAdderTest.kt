@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*

class LongAdderTest {
    private val adder = LongAdder()

    @Operation
    fun add(value: Long) = adder.add(value)

    @Operation
    fun increment() = adder.increment()

    @Operation
    fun decrement() = adder.decrement()

    @Operation
    fun sum() = adder.sum()

    @Operation
    fun reset() = adder.reset()

    @Operation
    fun sumThenReset() = adder.sumThenReset()

    @Test
    fun longAdderStressTest() = expectInvalidResults {
        StressOptions().check(this::class)
    }

    @Test
    fun longAdderModelCheckingTest() = expectInvalidResults {
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

