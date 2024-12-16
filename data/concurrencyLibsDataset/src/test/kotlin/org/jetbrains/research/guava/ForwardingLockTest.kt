@file:Suppress("HasPlatformType")

package org.jetbrains.research.guava

import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.jetbrains.kotlinx.lincheck.LincheckAssertionError
import org.jetbrains.kotlinx.lincheck.check
import org.junit.Test
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Param(name = "key", gen = IntGen::class, conf = "1:5")
abstract class AbstractSetTest(private val set: Set) {
    @Operation
    fun add(@Param(name = "key") key: Int): Boolean = set.add(key)

    @Operation
    fun remove(@Param(name = "key") key: Int): Boolean = set.remove(key)

    @Operation
    operator fun contains(@Param(name = "key") key: Int): Boolean = set.contains(key)

    @Test
    fun forwardingLockStressTest() = expectInvalidResults {
        StressOptions().check(this::class)
    }

    @Test
    fun forwardingLockModelCheckingTest() = expectInvalidResults {
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

class ForwardingLockSetTest : AbstractSetTest(ForwardingLockBasedSet())

interface Set {
    fun add(key: Int): Boolean
    fun remove(key: Int): Boolean
    fun contains(key: Int): Boolean
}

internal class ForwardingLockBasedSet : Set {
    private val set = mutableSetOf<Int>()
    private val lock = CustomForwardingLock(ReentrantLock())

    override fun add(key: Int): Boolean = lock.withLock {
        set.add(key)
    }

    override fun remove(key: Int): Boolean = lock.withLock {
        set.remove(key)
    }

    override fun contains(key: Int): Boolean = lock.withLock {
        set.contains(key)
    }
}

private inline fun <T> Lock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

class CustomForwardingLock(private val delegate: Lock) : Lock by delegate