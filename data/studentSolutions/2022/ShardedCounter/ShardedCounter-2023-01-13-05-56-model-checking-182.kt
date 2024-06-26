package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val lock = atomic(false)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
            val randIdx = Random.nextInt() % ARRAY_SIZE
            counters[randIdx].getAndIncrement()


        TODO("implement me!")
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var ans = 0;
        while (true) {
            if (lock.compareAndSet(false, true)) {
                for (i in 0 until counters.size) {
                    ans += counters[i].value
                }
                lock.value = false
                break
            }
        }
        return ans
        TODO("implement me!")
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME