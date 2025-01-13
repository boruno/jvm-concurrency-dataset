//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E: Any> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun assist() {
        repeat(tasksForCombiner.size) {
            val taskCell = tasksForCombiner[it]
            when (val task = taskCell.value) {
                null -> /* no task */ return@repeat
                is DequeueResult -> /* not a request */ return@repeat
                ENQUEUE_DONE -> /* not a request */ return@repeat
                DEQUEUE_TASK -> taskCell.value = DequeueResult(queue.removeFirstOrNull())
                else -> {
                    queue.addLast(task as E)
                    taskCell.value = ENQUEUE_DONE
                }
            }
        }
    }

    override fun enqueue(element: E) {
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                // We've got the lock!
                try {
                    queue.addLast(element)
                    assist()
                    return
                } finally {
                    combinerLock.value = false
                }
            }
            val cellId = randomCellIndex()
            val cell = tasksForCombiner[cellId]
            if (!cell.compareAndSet(null, element)) {
                continue
            }
            repeat(TASKS_TIMEOUT) {
                if (cell.value == ENQUEUE_DONE) {
                    return
                }
                Thread.onSpinWait()
            }
            if (cell.compareAndSet(element, null)) {
                continue
            }
            assert(cell.value == ENQUEUE_DONE)
            cell.value = null
        }
    }

    override fun dequeue(): E? {
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    return queue.removeFirstOrNull()
                        .also { assist() }
                } finally {
                    combinerLock.value = false
                }
            }
            val cellId = randomCellIndex()
            val cell = tasksForCombiner[cellId]
            if (!cell.compareAndSet(null, DEQUEUE_TASK)) {
                continue
            }
            repeat(TASKS_TIMEOUT) {
                val value = cell.value
                if (value is DequeueResult) {
                    return value.resp as E?
                }
                Thread.onSpinWait()
            }
            if (cell.compareAndSet(DEQUEUE_TASK, null)) {
                continue
            }
            val value = cell.value
            assert(value is DequeueResult)
            return (value as DequeueResult).resp as E?
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private class DequeueResult(val resp: Any?)

    private val DEQUEUE_TASK = Any()

    private val ENQUEUE_DONE = Any()
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private const val TASKS_TIMEOUT = 3