//package day4

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun combinerWork() {
        repeat(TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[it].value
            when (task) {
                null -> Unit
                is Dequeue -> tasksForCombiner[it].compareAndSet(task, Result(queue.removeFirstOrNull()))
                !is Result<*> -> {
                    queue.addLast(task as E)
                    tasksForCombiner[it].compareAndSet(task, Result(task))
                }
            }
        }
        combinerLock.value = false
    }

    override fun enqueue(element: E) {
        var indexWithResult: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {

                queue.addLast(element)
                if (indexWithResult != null) tasksForCombiner[indexWithResult!!].value = null

                combinerWork()
                return
            } else if (indexWithResult == null) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, element)) {
                    indexWithResult = index
                }
            } else {
                val value = tasksForCombiner[indexWithResult!!].value
                if (value is Result<*>) {
                    tasksForCombiner[indexWithResult!!].value = null
                    return
                }
            }
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
        }
    }

    override fun dequeue(): E? {
        var indexWithResult: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {

                val result = queue.removeFirstOrNull()
                if (indexWithResult != null) tasksForCombiner[indexWithResult!!].value = null

                combinerWork()
                return result
            } else if (indexWithResult == null) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, Dequeue)) {
                    indexWithResult = index
                }
            } else {
                val value = tasksForCombiner[indexWithResult!!].value
                if (value is Result<*>) {
                    tasksForCombiner[indexWithResult!!].value = null
                    return value.value as E
                }
            }
        }

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