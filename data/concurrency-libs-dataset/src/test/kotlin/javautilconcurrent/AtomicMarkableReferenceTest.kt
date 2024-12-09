@file:Suppress("HasPlatformType")

package javautilconcurrent

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.atomic.*

class AtomicMarkableReferenceTest {
    private val reference = AtomicMarkableReference(0, false)

    @Operation
    fun set(newRef: Int, newMark: Boolean) = reference.set(newRef, newMark)

    @Operation
    fun getReference() = reference.reference

    @Operation
    fun isMarked() = reference.isMarked

    @Operation
    fun compareAndSet(expectedReference: Int, newReference: Int, expectedMark: Boolean, newMark: Boolean) =
        reference.compareAndSet(expectedReference, newReference, expectedMark, newMark)

    @Operation
    fun get(): Pair<Int, Boolean> {
        val markHolder = BooleanArray(1)
        val ref = reference.get(markHolder)
        return ref to markHolder[0]
    }

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
