//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private inline fun withLock(block: () -> Unit): Boolean {
        val managedToLock = combinerLock.compareAndSet(expect = false, update = true)

        if (managedToLock) {
            try {
                block()
            } finally {
                combinerLock.value = false
            }
        }

        return managedToLock
    }

    private inline fun Boolean.orRun(block: () -> Unit) {
        if (!this) {
            block()
        }
    }

    private fun combine() {
        for (it in 0 until tasksForCombiner.size) {
            when (val task = tasksForCombiner[it].value) {
                null, PROCESSED, is FlatCombiningQueue<*>.DequeResult -> {}
                DEQUE_TASK -> {
                    tasksForCombiner[it].compareAndSet(DEQUE_TASK, DequeResult(queue.removeFirstOrNull()))
                }
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    queue.addLast(task as E)
                    tasksForCombiner[it].compareAndSet(task, PROCESSED)
                }
            }
        }
    }

    override fun enqueue(element: E) {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to the element. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.

        withLock {
            queue.addLast(element)
            combine()
        }.orRun {
            val index = acquireRandomCellWith(element)

            while (true) {
                if (tasksForCombiner[index].compareAndSet(PROCESSED, null)) {
                    return
                }

                withLock {
                    combine()
                    tasksForCombiner[index].compareAndSet(PROCESSED, null)
                    return
                }
            }
        }
    }

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

        withLock {
            return queue.removeFirstOrNull().also {
                combine()
            }
        }.orRun {
            val index = acquireRandomCellWith(DEQUE_TASK)

            while (true) {
                val task = tasksForCombiner[index].value

                if (task is FlatCombiningQueue<*>.DequeResult) {
                    @Suppress("UNCHECKED_CAST")
                    return (task.value as E).also {
                        tasksForCombiner[index].compareAndSet(task, null)
                    }
                }

                withLock {
                    tasksForCombiner[index].compareAndSet(DEQUE_TASK, null)

                    return queue.removeFirstOrNull().also {
                        combine()
                    }
                }
            }
        }

        error("Should not be here")
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private fun acquireRandomCellWith(element: Any?): Int {
        while (true) {
            val index = randomCellIndex()

            if (tasksForCombiner[index].compareAndSet(null, element)) {
                return index
            }
        }
    }

    inner class DequeResult(val value: E?)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = object : Any() {
    override fun toString() = "DEQUE_TASK"
}

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = object : Any() {
    override fun toString() = "PROCESSED"
}