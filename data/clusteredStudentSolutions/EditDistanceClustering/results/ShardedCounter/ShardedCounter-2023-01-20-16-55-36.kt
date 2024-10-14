package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    val random = Random
    val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val i = random.nextInt(ARRAY_SIZE)
        if (random.nextInt() % 2 == 0) {
            counters[0].incrementAndGet()
        } else {
            counters[1].incrementAndGet()
        }
//        counters[i].incrementAndGet()
//        TODO("implement me!")
//         TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return counters[0].getAndAdd(counters[1].value)
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME
