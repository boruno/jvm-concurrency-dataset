package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val index =  Random.nextInt() % ARRAY_SIZE
        while (true){
            if(counters[index].compareAndSet(counters[index].value, counters[index].value + 1)) {
                return
            }
        }
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var cnt = 0;
        for (x in 0 until ARRAY_SIZE){
            cnt += counters[x].value
        }
        return cnt
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME