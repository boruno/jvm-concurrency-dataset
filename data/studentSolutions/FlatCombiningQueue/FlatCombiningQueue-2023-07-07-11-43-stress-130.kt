package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun enqueueUnderLock(element: E) {
        require(combinerLock.value)
        queue.addLast(element)
    }

    private fun dequeueUnderLock(): E? {
        require(combinerLock.value)
        return queue.removeFirstOrNull()
    }
//
//    | ----------------------------------- |
//    |  Thread 1  |  Thread 2  | Thread 3  |
//    | ----------------------------------- |
//    | enqueue(2) | enqueue(2) | dequeue() |
//    | enqueue(1) | dequeue()  |           |
//    | ----------------------------------- |

    override fun enqueue(element: E) {
        if (combinerLock.compareAndSet(false, true)) {
            enqueueUnderLock(element)
            helpOthers()
            combinerLock.value = false
            return
        } else {
            while (true) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, element)) {
                    while (true) {
                        val value = tasksForCombiner[index].value
                        if (value is Result<*>) {
                            tasksForCombiner[index].compareAndSet(value, null)
                            return
                        } else {
                            if (combinerLock.compareAndSet(false, true)) {
                                helpOthers()
                                combinerLock.value = false
                                tasksForCombiner[index].compareAndSet(value, null)
                                return
                            }
                        }
                    }
                }
            }
        }

        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with the element. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
    }

    override fun dequeue(): E? {
        if (combinerLock.compareAndSet(false, true)) {
            val value = dequeueUnderLock()
            helpOthers()
            combinerLock.value = false
            return value
        } else {
            while (true) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, Dequeue)) {
                    while (true) {
                        val result = tasksForCombiner[index].value

                        if (result is Result<*>) {
                            tasksForCombiner[index].compareAndSet(result, null)
                            return result.value as? E
                        }

                        if (combinerLock.compareAndSet(false, true)) {
                            helpOthers()
                            combinerLock.value = false
                            val result = tasksForCombiner[index].value as? Result<E> ?: error("wq")
                            tasksForCombiner[index].compareAndSet(result, null)
                            return result.value
                        }
                    }
                }
            }
        }
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with `Dequeue`. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
//        return queue.removeFirstOrNull()
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    fun helpOthers() {
        assert(combinerLock.value)

        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val value = tasksForCombiner[i].value ?: continue
            if (value is Dequeue) {
                val dequeueValue = dequeueUnderLock()
                tasksForCombiner[i].compareAndSet(value, Result<E?>(dequeueValue))
            } else if (value is Result<*>) {

            } else {
                enqueueUnderLock(value as E)
                tasksForCombiner[i].compareAndSet(value, Result<E>(value))
            }
        }
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)