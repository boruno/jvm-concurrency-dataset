package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        if (tryLock())
            enqueueWithLock(element)
        else {
            val taskIndex = addTask(element)
            while(!tryLock()) {
                getResult(taskIndex) {
                    return
                }
            }

            enqueueWithLock(element)
        }
    }

    private fun enqueueWithLock(element: E) {
        try {
            for (i in 0 until tasksForCombiner.size) {
                executeTask(i)
            }
            queue.add(element)
        } finally {
            unlock()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        if (tryLock())
            return dequeueWithLock()
        else {
            val taskIndex = addTask(Dequeue)
            while(!tryLock()) {
                val result = tasksForCombiner[taskIndex].value
                if (result is Result<*>) {
                    tasksForCombiner[taskIndex].value = null
                    return result.value as E
                }
            }
            return dequeueWithLock()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun dequeueWithLock(): E? {
        try {
            for (i in 0 until tasksForCombiner.size) {
                executeTask(i)
            }
            return queue.removeFirstOrNull()
        } finally {
            unlock()
        }
    }

    fun tryLock() = combinerLock.compareAndSet(false, true)

    fun unlock() {
        combinerLock.value = false
    }

    private fun addTask(input: Any?): Int {
        while(true) {
            val index = randomIndex()
            val cell = tasksForCombiner[index]
            if (cell.compareAndSet(null, input))
                return index
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun executeTask(i: Int) {
        val cell = tasksForCombiner[i]
        when (val value = cell.value) {
            null, is Result<*> -> {}
            Dequeue -> cell.value = Result(queue.removeFirstOrNull())
            else -> Result(queue.add(value as E))
        }
    }

    private inline fun getResult(i: Int, onResult: (Result<*>) -> Unit) {
        val cell = tasksForCombiner[i]
        val value = cell.value
        if (value is Result<*>) {
            cell.value = null
            onResult(value)
        }
    }

    private fun randomIndex() = ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private object Dequeue

private class Result<V>(val value: V)