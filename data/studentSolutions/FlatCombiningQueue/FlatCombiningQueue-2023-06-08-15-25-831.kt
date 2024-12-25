//package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun combinerPass()
    {
        for (i in 0 until  TASKS_FOR_COMBINER_SIZE)
        {
            val cell = tasksForCombiner[i]
            val operation = cell.value as Operation ?: continue
            if (operation.state == PROCESSED) continue
            if (operation.task == DEQUE_TASK)
            {
                val newOperation = Operation(queue.removeFirstOrNull(), PROCESSED)
                cell.compareAndSet(operation, newOperation)
            }
            else // push request case
            {
                queue.addLast(operation.task as E)
                val newOperation = Operation(operation.task, PROCESSED)
                cell.getAndSet(newOperation)
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

        var currentCellIndex : Int? = null

        while (true)
        {
            if(combinerLock.compareAndSet(expect = false, true)) // acquired lock
            {
                if (currentCellIndex != null) // cleanup our presence in the tasksForCombiner
                {
                    val operation = tasksForCombiner[currentCellIndex].value as Operation ?: continue
                    if (operation.state != PROCESSED)
                    {
                        queue.addLast(element)
                    }

                    tasksForCombiner[currentCellIndex].value = null
                    currentCellIndex = null
                }
                else // we never asked for help
                {
                    queue.addLast(element)
                }

                combinerPass()

                if (!combinerLock.compareAndSet(expect = true, false))
                    throw Exception("Illegal mutex state")

                return
            }

            else // lock is not acquired
            {
                if (currentCellIndex == null)
                {
                    currentCellIndex = randomCellIndex()
                    val operation = Operation(element) // push request
                    if (!tasksForCombiner[currentCellIndex].compareAndSet(expect = null, operation))
                    {
                        // failed to add the task
                        currentCellIndex = null
                        continue
                    }
                }

                // we have an active index
                val curOperation = tasksForCombiner[currentCellIndex].value as Operation
                if (curOperation.state == PROCESSED)
                {
                    // success
                    tasksForCombiner[currentCellIndex].value = null
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

        var currentCellIndex : Int? = null

        while (true) {
            if (combinerLock.compareAndSet(expect = false, true))
            {
                // acquired lock
                var res : E? = null
                if (currentCellIndex != null)
                {
                    val operation = tasksForCombiner[currentCellIndex].value as Operation
                    if (operation.state == PROCESSED)
                    {
                        res = operation.task as E?
                    }
                    else
                    {
                        res = queue.removeFirstOrNull()
                    }

                    tasksForCombiner[currentCellIndex].value = null
                    currentCellIndex = null
                }
                else
                {
                    res = queue.removeFirstOrNull()
                }

                combinerPass()

                if (!combinerLock.compareAndSet(true, false))
                    throw Exception("Illegal mutex state")

                return res
            }

            else
            {
                if (currentCellIndex == null)
                {
                    currentCellIndex = randomCellIndex()
                    val newOperation = Operation(DEQUE_TASK)
                    if (!tasksForCombiner[currentCellIndex].compareAndSet(expect = null, newOperation))
                    {
                        // failed to add the task
                        currentCellIndex = null
                        continue
                    }
                }

                val curOperation = tasksForCombiner[currentCellIndex].value as Operation
                if (curOperation.state == PROCESSED)
                {
                    tasksForCombiner[currentCellIndex].compareAndSet(curOperation, null)
                    currentCellIndex = null
                    return curOperation.task as E
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private data class Operation(
        val task: Any?,
        val state: Any? = null
    )
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()