//package day4

import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
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
        if (combinerLock.compareAndSet(false, true)) {
            try {
                queue.addLast(element)

                tryHelp()
            } finally {
                combinerLock.set(false)
            }
        } else {
            scheduleEnqueueOperationAndWaitUntilDone(element)
        }
    }

    override fun dequeue(): E? {
        if (combinerLock.compareAndSet(false, true)) {
            try {
                val result = queue.removeFirstOrNull()

                tryHelp()

                return result
            } finally {
                combinerLock.set(false)
            }
        } else {
            return scheduleDequeOperationAndWaitUntilDone(Dequeue)
        }
    }


    private fun scheduleEnqueueOperationAndWaitUntilDone(element: E) {
        val randomCellIndex = putOperationInRandomCellAndGetCelLIndex(element)

        while (true) {
            if (tasksForCombiner.get(randomCellIndex) is Result<*>) {
                tasksForCombiner.set(randomCellIndex, null)
                return
            }
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    queue.addLast(element)
                    tasksForCombiner.set(randomCellIndex, null)
                    return
                } finally {
                    combinerLock.set(false)
                }
            }
        }
    }

    private fun scheduleDequeOperationAndWaitUntilDone(operation: Dequeue): E? {
        val randomCellIndex = putOperationInRandomCellAndGetCelLIndex(operation)

        while (true) {
            val cell = tasksForCombiner.get(randomCellIndex)
            if (cell is Result<*>) {
                return cell.value as E
            }

            if (combinerLock.compareAndSet(false, true)) {
                try {
                    val result = queue.removeFirstOrNull()
                    tasksForCombiner.set(randomCellIndex, null)
                    return result
                } finally {
                    combinerLock.set(false)
                }
            }
        }
    }

    private fun <V> putOperationInRandomCellAndGetCelLIndex(operation: V): Int {
        var randomCellIndex = randomCellIndex()
        while (!tasksForCombiner.compareAndSet(randomCellIndex, null, Result(operation))) {
            randomCellIndex = randomCellIndex()
        }
        return randomCellIndex
    }

    private fun tryHelp() {
        for (index in 0 until tasksForCombiner.length()) {
            when (tasksForCombiner.get(index)) {
                null -> continue
                is Dequeue -> tasksForCombiner.set(index, Result(queue.removeFirstOrNull()))
                !is Result<*> -> {
                    queue.addLast(tasksForCombiner.get(index) as E)
                    tasksForCombiner.set(index, null)
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