//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private val index = atomic(-1)
    private class Result<E>(val result : E?)

    @Suppress("UNCHECKED_CAST")
    private fun doTasks() {
        index.compareAndSet(-1, 0)
        do {
            val i = index.value
            val cell = tasksForCombiner[i]
            val task = cell.value
            if (task != null && task !is Result<*>) {
                if (task == DEQUE_TASK) {
                    val result = queue.removeFirstOrNull()
                    while (!cell.compareAndSet(task, Result(result))) {
                    }
                } else {
                    queue.addLast(task as E)
                    while (!cell.compareAndSet(task, PROCESSED)) {
                    }
                }
            }
        } while (index.incrementAndGet() < TASKS_FOR_COMBINER_SIZE)
        index.value = -1
    }

    override fun enqueue(element: E) {
        var index: Int
        while (true) {
            index = randomCellIndex()
            val cell = tasksForCombiner[index]
            if (cell.compareAndSet(null, element)) {
                break
            }
        }
        val cell = tasksForCombiner[index]
        while (!cell.compareAndSet(PROCESSED, null)) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                doTasks()
                combinerLock.compareAndSet(true, update = false)
                cell.compareAndSet(PROCESSED, null)
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        var index: Int
        while (true) {
            index = randomCellIndex()
            val cell = tasksForCombiner[index]
            if (cell.compareAndSet(null, DEQUE_TASK)) {
                break
            }
        }
        val cell = tasksForCombiner[index]
        while (cell.value == DEQUE_TASK) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                doTasks()
                combinerLock.compareAndSet(true, update = false)
                break
            }
        }
        return (cell.getAndSet(null) as Result<E?>).result
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()
private val EMPTY = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()