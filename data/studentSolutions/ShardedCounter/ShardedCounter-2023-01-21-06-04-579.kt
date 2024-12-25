//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private var lock = false

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        while(true) {
            val shardId = Random.nextInt(ARRAY_SIZE)
            if (!lock) {
                counters[shardId].getAndIncrement()
                break
            }
        }

    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0;
        lock = true
        for (i in 0 until ARRAY_SIZE){
            sum += counters[i].value
        }
        lock = false
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME
/* Если для любого исполнения существует эквивалентное
допустимое последовательное исполнение, сохраняющее отношение "произошло до".
Если при суммировании индекс i ещё не дошёл до инкрементируемого shardа, то это легко можем линеаризовать.
Если инкреметим индекс раньше проходимого, то проблема. Значит нужно поставить блокировки.
*/