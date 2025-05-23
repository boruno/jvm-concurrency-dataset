//package day4

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
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
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                queue.addLast(element)
                processQueue()
                combinerLock.compareAndSet(true, false)
                return
            } else {
                val id = randomCellIndex()
                if (tasksForCombiner[id].compareAndSet(null, element)) {
                    while (true) {
                        if (combinerLock.compareAndSet(false, true)) {
                            queue.addLast(element)
                            processQueue()
                            return
                        } else {
                            val value = tasksForCombiner[id].value
                            if (value is Result<*>) {
                                tasksForCombiner[id].compareAndSet(value, null)
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    override fun dequeue(): E? {
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
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                val res = queue.removeFirstOrNull()
                processQueue()
                return res
            } else {
                val id = randomCellIndex()
                if (tasksForCombiner[id].compareAndSet(null, Dequeue)) {
                    while (true) {
                        if (combinerLock.compareAndSet(false, true)) {
                            val res = queue.removeFirstOrNull()
                            processQueue()
                            return res
                        } else {
                            val value = tasksForCombiner[id].value
                            if (value is Result<*>) {
                                tasksForCombiner[id].compareAndSet(value, null)
                                return value.value as E
                            }
                        }
                    }
                }
            }
        }
    }

    private fun processQueue() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val value = tasksForCombiner[i].value
            when (value) {
                is Dequeue -> tasksForCombiner[i].compareAndSet(value, Result(queue.removeFirstOrNull()))
                is Result<*>, null -> continue
                else -> {
                    queue.addLast(value as E)
                    tasksForCombiner[i].compareAndSet(value, Result(null))
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)