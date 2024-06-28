package day2

import day1.*
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
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to the element. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        if (combinerLock.compareAndSet(expect = false, update = true)) {
            // The current thread is now the combiner.
            try {
                queue.addLast(element)

                for (i in 0 until tasksForCombiner.size) {
                    val task = tasksForCombiner[i].value
                    if (task != null) {
                        queue.addLast(task as E)
                        tasksForCombiner[i].value = PROCESSED
                    }
                }
            } finally {
                combinerLock.value = false
            }
        } else {
            while (true) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(expect = null, update = element)) {
                    // Wait until the operation is processed or the combiner lock becomes available.
                    while (true) {
                        if (combinerLock.value == false || tasksForCombiner[index].value == PROCESSED) {
                            // Clean up after the operation has been processed.
                            tasksForCombiner[index].compareAndSet(expect = PROCESSED, update = null)
                            break
                        }
                    }
                    break
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
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to `DEQUE_TASK`. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        if (combinerLock.compareAndSet(expect = false, update = true)) {
            // The current thread is now the combiner.
            try {
                // Perform the dequeue operation.
                val result = queue.removeFirstOrNull()

                // Process the operations announced by other threads.
                for (i in 0 until tasksForCombiner.size) {
                    val task = tasksForCombiner[i].value
                    if (task != null) {
                        queue.removeFirstOrNull()
                        tasksForCombiner[i].value = PROCESSED
                    }
                }

                return result
            } finally {
                // Always release the lock, even if an exception occurs.
                combinerLock.value = false
            }
        } else {
            // Another thread is currently the combiner.
            // TODO: Announce the dequeue operation in `tasksForCombiner`.
            while (true) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(expect = null, update = DEQUE_TASK)) {
                    // Wait until the operation is processed or the combiner lock becomes available.
                    while (true) {
                        if (combinerLock.value == false || tasksForCombiner[index].value == PROCESSED) {
                            // Clean up after the operation has been processed.
                            tasksForCombiner[index].compareAndSet(expect = PROCESSED, update = null)
                            break
                        }
                    }
                    break
                }
            }
            return null
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()