//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        var taskIndex = -1
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                if (taskIndex < 0 || tasksForCombiner[taskIndex].getAndSet(null) != PROCESSED) {
                    queue.addLast(element)
                }
                helpOthers()
                combinerLock.value = false
                return
            } else {
                if (taskIndex < 0) {
                    taskIndex = randomCellIndex()
                    if (!tasksForCombiner[taskIndex].compareAndSet(null, element)) {
                        taskIndex = -1
                    }
                } else {
                    if (tasksForCombiner[taskIndex].compareAndSet(PROCESSED, null)) {
                        return
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        //return queue.removeFirstOrNull()
        var taskIndex = -1
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                val result = if (taskIndex >= 0) {
                    tasksForCombiner[taskIndex].getAndSet(null) as? DequeueResult<*>
                } else {
                    null
                }
                val toReturn = if (result == null) {
                    queue.removeFirstOrNull()
                } else {
                    result.value
                }
                helpOthers()
                combinerLock.value = false
                return toReturn as E?
            } else {
                if (taskIndex < 0) {
                    taskIndex = randomCellIndex()
                    if (!tasksForCombiner[taskIndex].compareAndSet(null, DEQUE_TASK)) {
                        taskIndex = -1
                    }
                } else {
                    val result = tasksForCombiner[taskIndex].value
                    if (result is DequeueResult<*>) {
                        tasksForCombiner[taskIndex].value = null
                        return result.value as E?
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun helpOthers() {
        for (i in 0 until tasksForCombiner.size) {
            when (val value = tasksForCombiner[i].value) {
                DEQUE_TASK -> tasksForCombiner[i].value = DequeueResult(queue.removeFirstOrNull())
                PROCESSED, null -> continue
                else -> {
                    queue.addLast(value as E)
                    tasksForCombiner[i].value = PROCESSED
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private class DequeueResult<E>(val value: E?)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private val DEQUE_TASK = Any()

private val PROCESSED = Any()