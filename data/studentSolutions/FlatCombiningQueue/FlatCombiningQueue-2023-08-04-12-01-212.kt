//package day4

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

        val myIdx = randomCellIndex()
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                // became a combiner

                // check if it's operation is in the array
                val alreadyDone = when (val value = tasksForCombiner[myIdx].value) {
                    // mine element, remove
                    element -> {
                        tasksForCombiner[myIdx].compareAndSet(element, null)
                        false
                    }

                    // already done
                    is Result<*> -> {
                        tasksForCombiner[myIdx].compareAndSet(value, null)
                        true
                    }

                    // it is null
                    else -> false
                }

                // do the operation if it is necessary
                if (!alreadyDone) {
                    queue.addLast(element)
                }

                processTasksForOthers()

                // release the lock
                combinerLock.compareAndSet(true, false)
                return
            } else {
                // there is another combiner

                // try to set the element to tasks

                when (val value = tasksForCombiner[myIdx].value) {
                    // operation is already done
                    is Result<*> -> {
                        // clean up
                        tasksForCombiner[myIdx].compareAndSet(value, null)
                        // finish
                        return
                    }

                    // empty, set the element
                    null -> tasksForCombiner[myIdx].compareAndSet(null, element)

                    // element is already set but not processed
                    else -> Unit
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
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with `Dequeue`. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        var result: E? = null
        val myIdx = randomCellIndex()
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                // became a combiner

                // check if it's operation is in the array
                val alreadyDone = when (val value = tasksForCombiner[myIdx].value) {
                    // mine command, remove
                    Dequeue -> {
                        tasksForCombiner[myIdx].compareAndSet(Dequeue, null)
                        false
                    }

                    // already done
                    is Result<*> -> {
                        // clean up
                        tasksForCombiner[myIdx].compareAndSet(value, null)
                        // remember the result
                        result = value.value as E?
                        true
                    }

                    // it is null
                    else -> false
                }

                // do the operation if it is necessary
                if (!alreadyDone) {
                    result = queue.removeFirstOrNull()
                }

                processTasksForOthers()

                // release the lock
                combinerLock.compareAndSet(true, false)
                // get out of the cycle
                break
            } else {
                // there is another combiner

                // try to set the element to tasks
                when (val value = tasksForCombiner[myIdx].value) {
                    // operation is already done
                    is Result<*> -> {
                        // clean up
                        tasksForCombiner[myIdx].compareAndSet(value, null)
                        // finish
                        result = value.value as E?
                        // get out of the loop
                        break
                    }

                    // empty, set the command
                    null -> tasksForCombiner[myIdx].compareAndSet(null, Dequeue)

                    // the command is already set but not processed
                    else -> Unit
                }
            }
        }
        return result
    }

    private fun processTasksForOthers() {
        // process tasks for the others
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            when (val value = tasksForCombiner[i].value) {
                Dequeue -> {
                    val result = queue.removeFirstOrNull()
                    tasksForCombiner[i].compareAndSet(value, Result(result))
                }

                // empty, do nothing
                null -> continue

                // already done
                is Result<*> -> continue

                // enqueue
                else -> {
                    queue.addLast(value as E)
                    tasksForCombiner[i].compareAndSet(value, Result(value))
                }
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