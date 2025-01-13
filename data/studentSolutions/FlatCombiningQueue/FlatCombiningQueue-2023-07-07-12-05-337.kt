//package day4

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Op>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        val op = Enqueue(element)
        while (true) {
            val result = locking { queue.addLast(element) }
            if (result is Option.Some) {
                return result.v
            }

            val index = randomCellIndex()
            if (!tasksForCombiner[index].compareAndSet(null, op)) {
                continue
            }
            while (true) {
                if (tasksForCombiner[index].value is EnqueueResult) {
                    tasksForCombiner[index].value = null
                    return
                }
                locking {
                    if (tasksForCombiner[index].compareAndSet(op, null)) {
                        return queue.addLast(element)
                    }
                    require(tasksForCombiner[index].value is EnqueueResult) {
                        "Unexpected op at $index: ${tasksForCombiner[index].value}"
                    }
                    tasksForCombiner[index].value = null
                }
            }
        }
    }

    override fun dequeue(): E? {
        val op = Dequeue
        while (true) {
            val result = locking { queue.removeFirstOrNull() }
            if (result is Option.Some) {
                return result.v
            }

            val index = randomCellIndex()
            if (!tasksForCombiner[index].compareAndSet(null, op)) {
                continue
            }
            while (true) {
                val v = tasksForCombiner[index].value
                if (v is DequeueResult<*>) {
                    tasksForCombiner[index].value = null
                    @Suppress("UNCHECKED_CAST")
                    return v.value as E?
                }
                locking {
                    if (tasksForCombiner[index].compareAndSet(op, null)) {
                        return queue.removeFirstOrNull()
                    }
                    val v = tasksForCombiner[index].value
                    require(v is DequeueResult<*>) {
                        "Unexpected op at $index: $v"
                    }
                    tasksForCombiner[index].value = null
                    @Suppress("UNCHECKED_CAST")
                    return v.value as E?
                }
            }
        }
    }

    private fun assist() {
        for (i in 0 until tasksForCombiner.size) {
            when (val v = tasksForCombiner[i].value) {
                is Dequeue -> {
                    val result = queue.removeFirstOrNull()
                    tasksForCombiner[i].value = DequeueResult(result)
                    continue
                }

                is Enqueue<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    queue.addLast(v.value as E)
                    tasksForCombiner[i].value = EnqueueResult
                    continue
                }
            }
        }
    }

    private inline fun <R> locking(f: () -> R): Option<R> {
        if (!combinerLock.compareAndSet(false, true))
            return Option.None()
        try {
            return Option.Some(f())
        } finally {
            assist()
            combinerLock.value = false
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private sealed interface Option<V> {
    class None<V> : Option<V>
    class Some<V>(val v: V) : Option<V>
}

private interface Op

private object Dequeue : Op

private class DequeueResult<V>(
    val value: V
) : Op

private class Enqueue<V>(
    val value: V
) : Op

private object EnqueueResult : Op