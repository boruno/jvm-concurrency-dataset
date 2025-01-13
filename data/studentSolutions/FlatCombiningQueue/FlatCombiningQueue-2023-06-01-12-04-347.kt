//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private val index = atomic(-1)
    private class Result<E>(val result : E)

    @Suppress("UNCHECKED_CAST")
    private fun doTasks() {
        index.compareAndSet(-1, 0)
        do {
            val i = index.value
            val cell = tasksForCombiner[i]
            val task = cell.value
            if (task != null && task !is Result<*> && task != EMPTY) {
                if (task == DEQUE_TASK) {
                    val result = queue.removeFirstOrNull() ?: EMPTY
                    while (!cell.compareAndSet(task, Result(result))) {
                    }
                } else {
                    queue.addLast(task as E)
                    while (!cell.compareAndSet(task, null)) {
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
        while (this.index.value == -1 || index <= this.index.value) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                doTasks()
                combinerLock.compareAndSet(true, update = false)
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
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
        var index: Int
        while (true) {
            index = randomCellIndex()
            val cell = tasksForCombiner[index]
            if (cell.compareAndSet(null, DEQUE_TASK)) {
                break
            }
        }
        val cell = tasksForCombiner[index]
        while (this.index.value == -1 || index <= this.index.value) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                doTasks()
                combinerLock.compareAndSet(true, update = false)
                break
            }
        }
        return (cell.getAndSet(null) as Result<E>).result
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
//private val PROCESSED = Any()