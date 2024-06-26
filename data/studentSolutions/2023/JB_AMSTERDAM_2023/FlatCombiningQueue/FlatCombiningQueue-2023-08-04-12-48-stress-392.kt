package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E : Any> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        if (lock()) {
            // TODO: 2a. On success, apply this operation and help others by traversing
            // TODO:     `tasksForCombiner`, performing the announced operations, and
            // TODO:      updating the corresponding cells to `Result`.
            queue.addLast(element)
            helpOthers()
            unlock()
        } else {
            // TODO: 2b. If the lock is already acquired, announce this operation in
            // TODO:     `tasksForCombiner` by replacing a random cell state from
            // TODO:      `null` with the element. Wait until either the cell state
            // TODO:      updates to `Result` (do not forget to clean it in this case),
            // TODO:      or `combinerLock` becomes available to acquire.
            val cellIndex = announceOperation(element)

            while (true) {
                val value = tasksForCombiner[cellIndex].value

                if (value is Result<*>) {
                    tasksForCombiner[cellIndex].value = null
                    return
                }

                if (lock()) {
                    helpOthers()
                    unlock()
                    return
                }
            }
        }
    }

    override fun dequeue(): E? {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        if (lock()) {
            // TODO: 2a. On success, apply this operation and help others by traversing
            // TODO:     `tasksForCombiner`, performing the announced operations, and
            // TODO:      updating the corresponding cells to `Result`.
            val result = queue.removeFirstOrNull()
            helpOthers()
            unlock()
            return result
        } else {
            // TODO: 2b. If the lock is already acquired, announce this operation in
            // TODO:     `tasksForCombiner` by replacing a random cell state from
            // TODO:      `null` with `Dequeue`. Wait until either the cell state
            // TODO:      updates to `Result` (do not forget to clean it in this case),
            // TODO:      or `combinerLock` becomes available to acquire.
            val cellIndex = announceOperation(Dequeue)
            while (true) {
                val value = tasksForCombiner[cellIndex].value

                if (value is Result<*>) {
                    val value = tasksForCombiner[cellIndex].value as Result<E>
                    tasksForCombiner[cellIndex].value = null
                    return value.value
                }

                if (lock()) {
                    helpOthers()
                    val result = if (tasksForCombiner[cellIndex].value is Result<*>) {
                        val valuee = tasksForCombiner[cellIndex].value as Result<E>
                        tasksForCombiner[cellIndex].value = null
                        valuee.value
                    } else {
                        queue.removeFirstOrNull()
                    }
                    unlock()
                    return result
                }
            }
        }
    }

    private fun lock(): Boolean =
        combinerLock.compareAndSet(false, true)

    private fun unlock() {
        combinerLock.compareAndSet(true, false)
    }

    private fun helpOthers() {
        var index = 0
        while (index < TASKS_FOR_COMBINER_SIZE) {
            val value = tasksForCombiner[index].value

            when {
                value is Dequeue -> {
                    val dequedValue = queue.removeFirstOrNull()
                    tasksForCombiner[index].value = Result(dequedValue)
                }

                value != null -> {
                    queue.addLast(value as E)
                    tasksForCombiner[index].value = Result(value)
                }
            }

            index++
        }
    }

    private fun announceOperation(element: Any): Int {
        var cellIndex = randomCellIndex()
        while (!tasksForCombiner[cellIndex].compareAndSet(null, element)) {
            cellIndex = randomCellIndex()
        }

        return cellIndex
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)