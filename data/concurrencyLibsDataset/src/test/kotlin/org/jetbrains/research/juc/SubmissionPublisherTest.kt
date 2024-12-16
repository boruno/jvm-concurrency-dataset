@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class SubmissionPublisherTest {
    private val publisher = SubmissionPublisher<Int>()

    @Operation
    fun submit(item: Int) = publisher.submit(item)

    @Operation
    fun close() = publisher.close()

    @Operation
    fun getNumberOfSubscribers() = publisher.numberOfSubscribers

    @Operation
    fun getMaxBufferCapacity() = publisher.maxBufferCapacity

    @Test
    fun submissionPublisherStressTest() = StressOptions().check(this::class)

    @Test
    fun submissionPublisherModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
