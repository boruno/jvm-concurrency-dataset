//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        while (true) {
            // Step 1. Try to become the combiner by acquiring the lock.
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                try {
                    // Step 2a. Apply the enqueue operation and help others.
                    queue.addLast(element)
                    helpOthers()
                } finally {
                    combinerLock.value = false // Release the lock.
                }
                break
            } else {
                // Step 2b: Announce the enqueue operation and wait for completion.
                var announced = false
                while (!announced) {
                    val randomIndex = randomCellIndex()
                    if (tasksForCombiner[randomIndex].compareAndSet(expect = null, update = element)) {
                        announced = true
                        while (tasksForCombiner[randomIndex].value !== PROCESSED) {
                            if (combinerLock.compareAndSet(expect = false, update = true)) {
                                tasksForCombiner[randomIndex].value = PROCESSED
                                helpOthers()
                                combinerLock.value = false
                                break
                            }
                        }
                        // Clean up the task cell.
                        tasksForCombiner[randomIndex].value = null
                    }
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            // Step 1. Try to become the combiner by acquiring the lock.
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                try {
                    // Step 2a. Apply the dequeue operation and help others.
                    val dequeued = queue.removeFirstOrNull()
                    helpOthers()
                    return dequeued
                } finally {
                    combinerLock.value = false // Release the lock.
                }
            } else {
                // Step 2b: Announce the dequeue operation and wait for completion.
                var announced = false
                while (!announced) {
                    val randomIndex = randomCellIndex()
                    if (tasksForCombiner[randomIndex].compareAndSet(expect = null, update = DEQUE_TASK)) {
                        announced = true
                        while (tasksForCombiner[randomIndex].value !== PROCESSED) {
                            if (combinerLock.compareAndSet(expect = false, update = true)) {
                                tasksForCombiner[randomIndex].value = PROCESSED
                                helpOthers()
                                combinerLock.value = false
                                break
                            }
                        }
                        // Clean up the task cell.
                        tasksForCombiner[randomIndex].value = null
                    }
                }
            }
        }
    }

    // Helper method to process tasks announced by other threads.
    private fun helpOthers() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value
            if (task == null) continue // Skip if there is no task in the cell.
            if (task === DEQUE_TASK) {
                // Process dequeue operation.
                queue.removeFirstOrNull()
            } else {
                // Process enqueue operation.
                @Suppress("UNCHECKED_CAST")
                queue.addLast(task as E)
            }
            // Mark the task as processed.
            tasksForCombiner[i].value = PROCESSED
        }
    }

    // Generate a random index for accessing the tasksForCombiner array.
    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private val DEQUE_TASK = Any() // Token for a dequeue operation.

private val PROCESSED = Any() // Token to mark a task as completed.