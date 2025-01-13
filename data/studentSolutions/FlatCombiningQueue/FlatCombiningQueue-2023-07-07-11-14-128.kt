//package day4

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun unlock() {
        combinerLock.value = false
    }

    private fun helpOthers() {
        var i = 0
        while (i < TASKS_FOR_COMBINER_SIZE) {
            val cell = tasksForCombiner[i].value ?: continue
            if (cell is Dequeue) {
                val result = queue.removeFirstOrNull()
                tasksForCombiner[i].value = Result(result)
            } else {
                val result = queue.addLast(cell as E)
                tasksForCombiner[i].value = Result(result)
            }
            i++
        }
    }

    private fun announceTask(task: Any?): Int {
        var randomIndex = randomCellIndex()
        while (!tasksForCombiner[randomIndex].compareAndSet(null, task)) {
            randomIndex = randomCellIndex()
        }
        return randomIndex
    }

    private fun tryBecomeCombiner(action: () -> Any): Any? {
        if (combinerLock.compareAndSet(false, true)) {
            val result = action()
            helpOthers()
            unlock()
            return result
        }
        return null
    }

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
            queue.addLast(element)
            helpOthers()
            unlock()
            return
        }

        val taskIndex = announceTask(element)

        while (true) {
            val cellValue = tasksForCombiner[taskIndex].value

            // if somebody already helped us out
            if (cellValue is Result<*>) {
                tasksForCombiner[taskIndex].compareAndSet(cellValue, null)
                return
            }

            // if we're lucky to become a combiner
            if (combinerLock.compareAndSet(false, true)) {

                val cellResult = tasksForCombiner[taskIndex].getAndSet(null)

                if (cellResult is Result<*>) {
                    return
                }

                queue.addLast(element)
                helpOthers()
                unlock()
                return
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

        if (combinerLock.compareAndSet(false, true)) {
            val result = queue.removeFirstOrNull()
            helpOthers()
            unlock()
            return result
        }

        val taskIndex = announceTask(Dequeue)

        while (true) {
            val cellValue = tasksForCombiner[taskIndex].value

            // if somebody already helped us out
            if (cellValue is Result<*>) {
                val result = tasksForCombiner[taskIndex].getAndSet(null)
                return (result as Result<*>).value as E?
            }

            // if we're lucky to become a combiner
            if (combinerLock.compareAndSet(false, true)) {
                val cellResult = tasksForCombiner[taskIndex].getAndSet(null)

                if (cellResult is Result<*>) {
                    return cellResult.value as E?
                }

                val result = queue.removeFirstOrNull()
                helpOthers()
                unlock()
                return result
            }
        }

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