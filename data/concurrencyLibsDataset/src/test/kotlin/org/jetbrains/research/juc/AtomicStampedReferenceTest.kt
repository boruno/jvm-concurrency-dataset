@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*

class AtomicStampedReferenceTest {
    private val reference = AtomicStampedReference(0, 0)

    @Operation
    fun set(newRef: Int, newStamp: Int) = reference.set(newRef, newStamp)

    @Operation
    fun getReference() = reference.reference

    @Operation
    fun getStamp() = reference.stamp

    @Operation
    fun compareAndSet(expectedReference: Int, newReference: Int, expectedStamp: Int, newStamp: Int) =
        reference.compareAndSet(expectedReference, newReference, expectedStamp, newStamp)

    @Operation
    fun get(): Pair<Int, Int> {
        val stampHolder = IntArray(1)
        val ref = reference.get(stampHolder)
        return ref to stampHolder[0]
    }

    @Test
    fun atomicStampedReferenceStressTest() = StressOptions().check(this::class)

    @Test
    fun atomicStampedReferenceModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
