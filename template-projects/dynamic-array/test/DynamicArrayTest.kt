package mpp.dynamicarray

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.paramgen.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.jetbrains.kotlinx.lincheck.verifier.*
import org.junit.*
import java.io.*

@Param(name = "index", gen = IntGen::class, conf = "0:5")
class DynamicArrayTest {
    private val q = DynamicArrayImpl<Int>()

    @Operation
    fun get(@Param(name = "index") index: Int) = q.get(index)

    @Operation
    fun put(@Param(name = "index") index: Int, element: Int) = q.put(index, element)

    @Operation
    fun pushBack(@Param(name = "index") element: Int) = q.pushBack(element)

    @Operation
    fun size() = q.size

    @Test
    fun modelCheckingTest() = try {
        ModelCheckingOptions()
            .iterations(100)
            .invocationsPerIteration(10000)
            .actorsBefore(0)
            .actorsAfter(0)
            .threads(3)
            .actorsPerThread(3)
            .checkObstructionFreedom()
            .sequentialSpecification(DynamicArrayIntSequential::class.java)
            .check(this::class.java)
    } catch (t: Throwable) {
        throw t
    }

    @Test
    fun stressTest() = try {
        StressOptions()
            .iterations(100)
            .invocationsPerIteration(50000)
            .actorsBefore(0)
            .actorsAfter(0)
            .threads(3)
            .actorsPerThread(3)
            .sequentialSpecification(DynamicArrayIntSequential::class.java)
            .check(this::class.java)
    } catch (t: Throwable) {
        throw t
    }
}

class DynamicArrayIntSequential : VerifierState() {
    private val array = ArrayList<Int>()

    fun get(index: Int): Int =
        if (index < array.size) array[index]
        else throw IllegalArgumentException()

    fun put(index: Int, element: Int): Unit =
        if (index < array.size) array[index] = element
        else throw IllegalArgumentException()

    fun pushBack(element: Int) {
        array.add(element)
    }

    fun size(): Int = array.size

    override fun extractState() = array
}