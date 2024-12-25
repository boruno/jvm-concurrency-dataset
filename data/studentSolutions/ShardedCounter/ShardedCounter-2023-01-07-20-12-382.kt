//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.Random
import kotlin.math.abs

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val randomizer = Random()

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val cell = abs(randomizer.nextInt() % ARRAY_SIZE)
        print("cell = ")
        println(cell)
        var cellValue = counters[cell].value
        cellValue += 1
        print("cell value = ")
        println(cellValue)
        counters[cell].value = cellValue
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var counterResult = 0
        for (i in 0 until counters.size) {
            counterResult += counters[i].value
        }
        print("counter result = ")
        println(counterResult)
        return counterResult
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME