//package day4

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
            while(true) {
                getResult(taskIndex) { return }
                if (tryLock()) {
                    processCombinerQueue()
                    unlock()
                }
            }
        }
    }

    private fun enqueueWithLock(element: E) {
        try {
            processCombinerQueue()
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
            while(true) {
                getResult(taskIndex) { return it.value as E }
                if (tryLock()) {
                    processCombinerQueue()
                    unlock()
                }
            }
        }
    }

    private fun dequeueWithLock(): E? {
        try {
            processCombinerQueue()
            return queue.removeFirstOrNull()
        } finally {
            unlock()
        }
    }

    private fun processCombinerQueue() {
        for (i in 0 until tasksForCombiner.size)
            executeTask(i)
    }

    private fun tryLock() = combinerLock.compareAndSet(false, true)

    private fun unlock() {
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

    private inline fun getResult(i: Int, clear: Boolean = false, onResult: (Result<*>) -> Unit) {
        val value = tasksForCombiner[i].value
        if (value is Result<*>) {
            tasksForCombiner[i].value = null
            onResult(value)
        }
    }

    private fun randomIndex() = ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private object Dequeue

private class Result<V>(val value: V)