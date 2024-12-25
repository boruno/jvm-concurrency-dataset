//package day4

import day1.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    // TODO: Make this code thread-safe using the flat-combining technique.
    // TODO: 1.  Try to become a combiner by
    // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
    // TODO: 2a. On success, apply this operation and help others by traversing
    // TODO:     `tasksForCombiner`, performing the announced operations, and
    // TODO:      updating the corresponding cells to `Result`.
    // TODO: 2b. If the lock is already acquired, announce this operation in
    // TODO:     `tasksForCombiner` by replacing a random cell state from
    // TODO:      `null` with the element. Wait until either the cell state
    // TODO:      updates to `Result` (do not forget to clean it in this case),
    // TODO:      or `combinerLock` becomes available to acquire.
    override fun enqueue(element: E) {
        if (tryLock()) {
            try {
                queue.addLast(element)

                tryHelp()
            } finally {
                tryUnlock()
            }
        } else {
            scheduleEnqueueOperationAndWaitUntilDone(element)
        }
    }

    override fun dequeue(): E? {
        if (tryLock()) {
            try {
                val result = queue.removeFirstOrNull()

                tryHelp()

                return result
            } finally {
                tryUnlock()
            }
        } else {
            return scheduleDequeOperationAndWaitUntilDone()
        }
    }


    private fun scheduleEnqueueOperationAndWaitUntilDone(element: E) {
        val randomCellIndex = putToRandomCellAndGetCellIndex(element)

        while (true) {
            if (tasksForCombiner.get(randomCellIndex) is Result<*>) {
                tasksForCombiner.set(randomCellIndex, null)
                return
            }
            if (tryLock()) {
                try {
                    queue.addLast(element)
                    tasksForCombiner.set(randomCellIndex, null)
                    tryHelp()
                    return
                } finally {
                    tryUnlock()
                }
            }
        }
    }

    private fun scheduleDequeOperationAndWaitUntilDone(): E? {
        val randomCellIndex = putToRandomCellAndGetCellIndex(Dequeue)

        while (true) {
            val cell = tasksForCombiner.get(randomCellIndex)
            if (cell is Result<*>) {
                val value = cell.value as E
                tasksForCombiner.set(randomCellIndex, null)
                return value
            }

            if (tryLock()) {
                try {
                    val result = queue.removeFirstOrNull()
                    tasksForCombiner.set(randomCellIndex, null)
                    tryHelp()
                    return result
                } finally {
                    tryUnlock()
                }
            }
        }
    }

    private fun tryUnlock() {
        combinerLock.set(false)
    }

    private fun tryLock() = combinerLock.compareAndSet(false, true)

    private fun <V> putToRandomCellAndGetCellIndex(operation: V): Int {
        var randomCellIndex = randomCellIndex()
        while (!tasksForCombiner.compareAndSet(randomCellIndex, null, operation)) {
            randomCellIndex = randomCellIndex()
        }
        return randomCellIndex
    }

    private fun tryHelp() {
        for (index in 0 until tasksForCombiner.length()) {
            when (val cell = tasksForCombiner.get(index)) {
                null -> continue
                is Result<*> -> continue
                is Dequeue -> {
                    val value = queue.removeFirstOrNull()
                    tasksForCombiner.set(index, Result(value))
                }

                else -> {
                    queue.addLast(cell as E)
                    tasksForCombiner.set(index, Result(null))
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)