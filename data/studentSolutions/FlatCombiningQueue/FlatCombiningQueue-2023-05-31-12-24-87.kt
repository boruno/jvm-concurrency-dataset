//package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        while (true) {
            val success = tryUnderLock {
                queue.addLast(element)
                handleTasks()
            }
            if (success) return

            val cellIndex = randomCellIndex()
            if (!tasksForCombiner[cellIndex].compareAndSet(EMPTY_TASK, element))
                continue

            while (true) {
                if (tasksForCombiner[cellIndex].compareAndSet(PROCESSED, EMPTY_TASK))
                    return

                val sec = tryUnderLock {
                    if(tasksForCombiner[cellIndex].getAndSet(EMPTY_TASK)  != PROCESSED)
                        queue.addLast(element)
                    handleTasks()
                }
                if (sec) return
            }
        }
    }

    override fun dequeue(): E? {
        var element : E? = null
        while (true) {
            val success = tryUnderLock {
                element = queue.removeFirstOrNull()
                handleTasks()
            }
            if (success)
                return element

            val cellIndex = randomCellIndex()
            if (!tasksForCombiner[cellIndex].compareAndSet(EMPTY_TASK, DEQUE_TASK))
                continue

            while (true) {
                val value = tasksForCombiner[cellIndex].value
                if (value != DEQUE_TASK) {
                    if (value == PROCESSED && tasksForCombiner[cellIndex].compareAndSet(PROCESSED, EMPTY_TASK))
                        return null
                    if (tasksForCombiner[cellIndex].compareAndSet(DEQUE_TASK, EMPTY_TASK))
                        return value as E?
                }

                val sec = tryUnderLock {
                    if(tasksForCombiner[cellIndex].getAndSet(EMPTY_TASK)  == DEQUE_TASK)
                        element = queue.removeFirstOrNull()
                    handleTasks()
                }
                if (sec)
                    return element
            }
        }
    }

    private fun handleTasks() {
        // TODO: help other
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            if (tasksForCombiner[i].value == EMPTY_TASK || tasksForCombiner[i].value == PROCESSED)
                continue
            // Dequeue task
            if (tasksForCombiner[i].value == DEQUE_TASK) {
                val element = queue.firstOrNull()
                tasksForCombiner[i].compareAndSet(DEQUE_TASK, element)
                continue
            }
            // Enqueue task
            val element = tasksForCombiner[i].value as? E ?: continue
            queue.addLast(element)
            tasksForCombiner[i].compareAndSet(element, PROCESSED)
        }
    }

    private fun randomCellIndex(): Int = ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private fun tryLock() : Boolean = combinerLock.compareAndSet(false, true)

    private fun unlock() = combinerLock.getAndSet(false)

    private fun tryUnderLock(action: () -> Unit) : Boolean {
        if (tryLock()) {
            try {
                action()
            }
            finally {
                unlock()
            }
            return true
        }

        return false
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private val EMPTY_TASK = null
private val DEQUE_TASK = Any()
private val PROCESSED = Any()