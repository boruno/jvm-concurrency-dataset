//package day4

import day1.*
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
        var lockSet = false
        var combinerCell = -1
        while (true) {
            lockSet = combinerLock.compareAndSet(false, true)
            if (lockSet) {
                queue.addLast(element)
                helpOthers()
                break
            } else if (combinerCell < 0) {
                val idx = randomCellIndex()
                if (tasksForCombiner.compareAndSet(idx, null, element)) {
                    combinerCell = idx
                }
            } else {
                // wait for result
                val cellValue = tasksForCombiner[combinerCell]
                if (cellValue is Result<*>) {
                    tasksForCombiner.set(combinerCell, null)
                    break
                }
            }
        }
        if (lockSet) {
            combinerLock.set(false)
        }
    }

    private fun helpOthers() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i] ?: continue
            if (task is Result<*>)
                continue
            if (task == Dequeue) {
                val element = queue.removeFirstOrNull()
                tasksForCombiner.set(i, Result(element))
            } else {
                queue.addLast(task as E)
                tasksForCombiner.set(i, Result(task))
            }
        }
    }

    override fun dequeue(): E? {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with `Dequeue`. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        var lockSet = false
        var combinerCell = -1
        try {
            while (true) {
                lockSet = combinerLock.compareAndSet(false, true)
                if (lockSet) {
                    val retval = queue.removeFirstOrNull()
                    helpOthers()
                    return retval
                } else if (combinerCell < 0) {
                    val idx = randomCellIndex()
                    if (tasksForCombiner.compareAndSet(idx, null, Dequeue)) {
                        combinerCell = idx
                    }
                } else {
                    // wait for result
                    val cellValue = tasksForCombiner[combinerCell]
                    if (cellValue is Result<*>) {
                        tasksForCombiner.set(combinerCell, null)
                        return cellValue.value as E
                    }
                }
            }
        } finally {
            if (lockSet) {
                combinerLock.set(false)
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
    val value: V?
)