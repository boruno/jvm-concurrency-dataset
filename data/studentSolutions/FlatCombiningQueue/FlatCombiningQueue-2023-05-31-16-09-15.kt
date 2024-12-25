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
            val enqueueTask = EnqueueTask(element)
            if (!tasksForCombiner[cellIndex].compareAndSet(EMPTY_TASK, enqueueTask))
                continue

            while (true) {
                if (tasksForCombiner[cellIndex].compareAndSet(ENQUEUE_RESULT, EMPTY_TASK))
                    return

                val sec = tryUnderLock {
                    if(tasksForCombiner[cellIndex].getAndSet(EMPTY_TASK) != ENQUEUE_RESULT)
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
            if (!tasksForCombiner[cellIndex].compareAndSet(EMPTY_TASK, DEQUEUE_TASK))
                continue

            while (true) {
                val value = tasksForCombiner[cellIndex].value
                if (value is DequeueResult<*>)
                    return value.value as E?

                val sec = tryUnderLock {
                    if(tasksForCombiner[cellIndex].getAndSet(EMPTY_TASK) != DEQUEUE_TASK)
                        element = queue.removeFirstOrNull()
                    handleTasks()
                }
                if (sec)
                    return element
            }
        }
    }

    private fun handleTasks() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            when (val value = tasksForCombiner[i].value) {
                EMPTY_TASK -> continue
                ENQUEUE_RESULT -> continue
                is DequeueResult<*> -> continue
                DEQUEUE_TASK -> {
                    val result = DequeueResult(queue.firstOrNull())
                    tasksForCombiner[i].compareAndSet(DEQUEUE_TASK, result)
                }
                is EnqueueTask<*> -> {
                    val element = value.value as E? ?: continue
                    queue.addLast(element)
                    tasksForCombiner[i].compareAndSet(value, ENQUEUE_RESULT)
                }
            }
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
private val DEQUEUE_TASK = DequeueTask()
private val ENQUEUE_RESULT = EnqueueResult()
private class DequeueTask()
private class DequeueResult<E>(val value: E?)
private class EnqueueTask<E>(val value: E)
private class EnqueueResult()
