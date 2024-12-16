@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class CountDownLatchTest {
    private val latch = CountDownLatch(1)

    @Operation
    fun countDown() = latch.countDown()

    @Operation
    fun await() = latch.await()

    @Operation
    fun getCount() = latch.count

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun countDownLatchStressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun countDownLatchModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
