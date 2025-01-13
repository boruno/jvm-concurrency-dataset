//package day4

import Queue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReferenceArray

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    /**
     * 1.  Try to become a combiner by changing `combinerLock` from `false` (unlocked) to `true` (locked).
     * 2a. On success, apply this operation and help others by traversing
     *     `tasksForCombiner`, performing the announced operations, and
     *      updating the corresponding cells to `Result`.
     * 2b. If the lock is already acquired, announce this operation in
     *     `tasksForCombiner` by replacing a random cell state from
     *      `null` with the element. Wait until either the cell state
     *      updates to `Result` (do not forget to clean it in this case),
     *      or `combinerLock` becomes available to acquire.
     */
    override fun enqueue(element: E) {
        val isLocked = tryLock()
        if (isLocked) {
            queue.addLast(element)
            for (idx in 0 until TASKS_FOR_COMBINER_SIZE) {
                when (val task = tasksForCombiner[idx]) {
                    is Result<*> -> {}
                    is Dequeue -> dequeue()
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        val elem = task as E
                        if (task != null) {
                            queue.addLast(elem)
                        }
                        tasksForCombiner[idx] = Result(elem)
                    }
                }
            }
        } else {
            val cell = randomCellIndex()
            tasksForCombiner[cell] = element
        }
        unlock()
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
        val isLocked = tryLock()
        if (isLocked) {
            val removedValue = queue.removeFirstOrNull()
            for (idx in 0 until TASKS_FOR_COMBINER_SIZE) {
                when (val task = tasksForCombiner[idx]) {
                    is Result<*> -> {}
                    is Dequeue -> dequeue()
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        val elem = task as E
                        if (task != null) {
                            queue.addLast(elem)
                        }
                        tasksForCombiner[idx] = Result(elem)
                    }
                }
            }
            return removedValue
        } else {
            val cell = randomCellIndex()
            tasksForCombiner[cell] = Dequeue
        }

        unlock()
        return null
    }

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun unlock() {
        combinerLock.set(false)
    }

    private fun randomCellIndex(): Int {
        return ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

/**
 * Put this token in `tasksForCombiner` for dequeue().
 * enqueue()-s should put the inserting element.
 */
private object Dequeue

/**
 * Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
 */
private class Result<V>(
    val value: V
)