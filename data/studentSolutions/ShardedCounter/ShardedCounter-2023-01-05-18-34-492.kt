//package mpp.counter

import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val locked: AtomicBoolean = atomic(false)


    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        while(locked.value) { }
        val ind = Random.nextInt() % ARRAY_SIZE
        counters[ind].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        while(!locked.compareAndSet(false, true)) { }
        var ans = 0
        for(i in 0..ARRAY_SIZE) {
            ans += counters[i].value
        }
        locked.value = false
        return ans
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME