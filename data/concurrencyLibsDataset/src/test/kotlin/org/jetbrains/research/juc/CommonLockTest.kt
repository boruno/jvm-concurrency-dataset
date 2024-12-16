package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.LincheckAssertionError
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.junit.Test

@Param(name = "key", gen = IntGen::class, conf = "1:5")
abstract class AbstractSetTest(private val set: Set) {
    @Operation
    fun add(@Param(name = "key") key: Int): Boolean = set.add(key)

    @Operation
    fun remove(@Param(name = "key") key: Int): Boolean = set.remove(key)

    @Operation
    operator fun contains(@Param(name = "key") key: Int): Boolean = set.contains(key)

    @Test
    fun commonLockStressTest() = expectInvalidResults {
        StressOptions().check(this::class)
    }

    @Test
    fun commonLockModelCheckingTest() = expectInvalidResults {
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

interface Set {
    fun add(key: Int): Boolean
    fun remove(key: Int): Boolean
    fun contains(key: Int): Boolean
}