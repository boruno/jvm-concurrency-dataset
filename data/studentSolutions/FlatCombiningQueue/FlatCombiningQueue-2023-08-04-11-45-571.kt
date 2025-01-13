//package day4

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
        var myIndex: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                if (myIndex == null) {
                    // we didn't put a task
                    queue.addLast(element)
                } else if (tasksForCombiner[myIndex].value !is Result<*>) {
                    // we put a task, but no one did it
                    queue.addLast(element)
                    tasksForCombiner[myIndex].value = null
                }
                if (myIndex !=  null) {
                    tasksForCombiner[myIndex].value = null
                }
                processTasks()
                combinerLock.value = false
                return
            } else if (myIndex == null) {
                val randomCellIndex = randomCellIndex()
                if (tasksForCombiner[randomCellIndex].compareAndSet(null, element)) {
                    myIndex = randomCellIndex
                }
            } else if (tasksForCombiner[myIndex].value is Result<*>) {
                // someone performed our task
                tasksForCombiner[myIndex].value = null
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
        var myIndex: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                var retValue: E? = null
                if (myIndex == null) {
                    // we didn't put a task
                    retValue = queue.removeFirstOrNull()
                } else {
                    val currentValue = tasksForCombiner[myIndex].value
                    if (currentValue is Result<*>) {
                        // someone performed our task
                        retValue = currentValue.value as E?
                    } else {
                        // we put a task, but no one did it
                        retValue = queue.removeFirstOrNull()
                    }
                    tasksForCombiner[myIndex].value = null
                }
                processTasks()
                combinerLock.value = false
               return retValue
            } else if (myIndex == null) {
                val randomCellIndex = randomCellIndex()
                if (tasksForCombiner[randomCellIndex].compareAndSet(null, Dequeue)) {
                    myIndex = randomCellIndex
                }
            } else if (tasksForCombiner[myIndex].value is Result<*>) {
                val value = tasksForCombiner[myIndex].value as Result<E>
                tasksForCombiner[myIndex].value = null
                return value.value
            }
        }
        return queue.removeFirstOrNull()
    }

    private fun processTasks() {
        repeat(TASKS_FOR_COMBINER_SIZE) {
            when (val value = tasksForCombiner[it].value) {
                Dequeue -> tasksForCombiner[it].compareAndSet(Dequeue, Result(queue.removeFirstOrNull()))
                null -> Unit
                else -> tasksForCombiner[it].compareAndSet(value, Result(null)) // enqueue
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