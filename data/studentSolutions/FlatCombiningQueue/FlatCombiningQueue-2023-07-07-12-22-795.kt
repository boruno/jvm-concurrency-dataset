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
                            val res = if (tasksForCombiner[id].compareAndSet(element, null)) {
                                queue.addLast(element)
                            } else {
                                enCheckAndClear(id)
                            }
                            // processQueue()
                            combinerLock.compareAndSet(true, false)
                            return
                        } else {
                            enCheckAndClear(id)
                        }
                    }
                }
            }
        }
    }

    private fun enCheckAndClear(id: Int) {
        val value = tasksForCombiner[id].value
        if (value is Result<*>) {
            tasksForCombiner[id].compareAndSet(value, null)
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
                combinerLock.compareAndSet(true, false)
                return res
            } else {
                val id = randomCellIndex()
                if (tasksForCombiner[id].compareAndSet(null, Dequeue)) {
                    while (true) {
                        if (combinerLock.compareAndSet(false, true)) {
                            val res = if (tasksForCombiner[id].compareAndSet(Dequeue, null)) {
                                queue.removeFirstOrNull()
                            } else {
                                deCheckAndClear(id)
                            }

                            // processQueue()

                            combinerLock.compareAndSet(true, false)
                            return res

                        } else {
                            deCheckAndClear(id)?.let { return it }
                        }
                    }
                }
            }
        }
    }

    private fun deCheckAndClear(id: Int): E? {
        val value = tasksForCombiner[id].value
        if (value is Result<*>) {
            tasksForCombiner[id].compareAndSet(value, null)
            return value.value as E
        }
        return null
    }

    private fun processQueue() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            while (true) {
                val value = tasksForCombiner[i].value
                when (value) {
                    is Dequeue -> {
                        tasksForCombiner[i].value = Result(queue.removeFirstOrNull())
                        break
                    }
                    is Result<*>, null -> break
                    else -> {
                        if (tasksForCombiner[i].compareAndSet(value, Result(null))) {
                            queue.addLast(value as E)
                            break
                        }
                    }
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