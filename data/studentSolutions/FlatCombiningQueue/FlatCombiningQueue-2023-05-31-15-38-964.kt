//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        var randomIndex = -1
        while (true) {
            // Step 1. Try to become the combiner by acquiring the lock.
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                try {
                    // Step 2a. Apply the enqueue operation and help others.
                    if (randomIndex == -1 || tasksForCombiner[randomIndex].value == element) {
                        queue.addLast(element)
                    }
                    if (randomIndex != -1) {
                        tasksForCombiner[randomIndex].value = null
                    }
                    helpOthers()
                } finally {
                    combinerLock.value = false // Release the lock.
                }
                break
            } else {
                randomIndex = randomCellIndex()
                if (!tasksForCombiner[randomIndex].compareAndSet(expect = null, update = element)) {
                    randomIndex = -1
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        println("dequeue")
        var randomIndex = -1
        while (true) {
            // Step 1. Try to become the combiner by acquiring the lock.
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                try {
                    val dequeued: E? = if (randomIndex == -1 || tasksForCombiner[randomIndex].value == DEQUE_TASK) {
                        queue.removeFirstOrNull()
                    } else {
                        if (tasksForCombiner[randomIndex].value == DEQUE_TASK || tasksForCombiner[randomIndex].value == null) {
                            val qq = 42
                        }
                        tasksForCombiner[randomIndex].value as E
                    }
                    if (randomIndex != -1) {
                        tasksForCombiner[randomIndex].value = null
                    }
                    helpOthers()
                    return dequeued
                } finally {
                    combinerLock.value = false // Release the lock.
                }
            } else {
                randomIndex = randomCellIndex()
                if (!tasksForCombiner[randomIndex].compareAndSet(expect = null, update = DEQUE_TASK)) {
                    randomIndex = -1
                }
            }
        }
    }

    // Helper method to process tasks announced by other threads.
    private fun helpOthers() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value ?: continue

            if (task === DEQUE_TASK) {
                tasksForCombiner[i].value = queue.removeFirstOrNull()
            }
            else { // task == enqueue
                @Suppress("UNCHECKED_CAST")
                queue.addLast(task as E)
                tasksForCombiner[i].value = PROCESSED
            }
        }
    }

    // Generate a random index for accessing the tasksForCombiner array.
    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private val DEQUE_TASK = Any() // Token for a dequeue operation.

private val PROCESSED = Any() // Token to mark a task as completed.