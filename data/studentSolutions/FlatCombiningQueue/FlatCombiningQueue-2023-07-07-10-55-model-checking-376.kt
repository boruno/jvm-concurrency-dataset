package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

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
        tryOperation(element)
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
        return tryOperation(Dequeue).value
    }

    private fun tryOperation(operation: Any?): Result<E?> {
        while (true) {
            if (tryLock()) {
                return processOperation(operation)
            }
            val index = randomCellIndex()
            if (tasksForCombiner[index].compareAndSet(null, operation)) {
                while (true) {
                    if (tryLock()) {
                        processOperation(operation, index)
                    }

                    val value = tasksForCombiner[index].value

                    if (value is Result<*> && tasksForCombiner[index].compareAndSet(value, null)) {
                        @Suppress("UNCHECKED_CAST")
                        return value as Result<E?>
                    }
                }
            }
        }
    }

    private fun processOperation(operation: Any?, index: Int? = null): Result<E?> {
        val result: Result<E?>
        if (index != null) {
            val value = tasksForCombiner[index].value
            result = if (value is Result<*>) {
                @Suppress("UNCHECKED_CAST")
                value as Result<E?>
            } else {
                tasksForCombiner[index].compareAndSet(value, null)
                doOperation(operation)
            }
        } else {
            result = doOperation(operation)
        }

        processWaitList()

        unlock()

        return result
    }

    private fun processWaitList() {
        repeat(tasksForCombiner.size) { i ->
            val maybeOparation = tasksForCombiner[i].value
            if (maybeOparation != null && maybeOparation !is Result<*>) {
                doOperation(maybeOparation)
            }
        }
    }

    private fun doOperation(operation: Any?): Result<E?> {
        return when (operation) {
            is Dequeue -> Result(queue.removeFirstOrNull())
            else -> {
                @Suppress("UNCHECKED_CAST")
                queue.addLast(operation as E)
                Result(null)
            }
        }
    }

    private fun tryLock(): Boolean = combinerLock.compareAndSet(expect = false, update = true)

    private fun unlock() {
        combinerLock.compareAndSet(expect = true, update = false)
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
