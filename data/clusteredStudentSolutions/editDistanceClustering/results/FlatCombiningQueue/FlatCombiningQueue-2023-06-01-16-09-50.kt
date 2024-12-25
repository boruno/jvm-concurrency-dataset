//package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class

FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun combinerPass()
    {
        for (i in 0 until  TASKS_FOR_COMBINER_SIZE)
        {
            val task = tasksForCombiner[i].value ?: continue
            if (task == DEQUE_TASK) {
                val queueElem = queue.firstOrNull()?: continue
                if (tasksForCombiner[i].compareAndSet(DEQUE_TASK, queueElem)) {
                    queue.removeFirst()
                } else {
                    continue
                }
            } else { // push request
                if (tasksForCombiner[i].compareAndSet(task, PROCESSED))
                    queue.addLast(task as E)
            }
        }
    }

    private fun cleanUp(i: Int)
    {
        tasksForCombiner[i].value = null
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

        var currentCell : Int? = null

        while (true)
        {
            if(combinerLock.compareAndSet(false, true)) // acquired lock
            {
                // becomes combiner

                if (currentCell == null) {
                    queue.addLast(element)
                } else {
                    if (tasksForCombiner[currentCell].value != PROCESSED) {
                        queue.addLast(element)
                    }

                    cleanUp(currentCell)
                    currentCell = null
                }

                combinerPass()

                if (!combinerLock.compareAndSet(true, false))
                    throw Exception("Illegal mutex state")

                return
            }
            else
            {
                // publishes and waits
                if (currentCell == null) // null means there is no cell with our task
                {
                    currentCell = randomCellIndex()
                    if (!tasksForCombiner[currentCell].compareAndSet(null, element)) {
                        currentCell = randomCellIndex()
                        continue
                    } else {
                        currentCell = null
                        continue
                    }
                }


                if (tasksForCombiner[currentCell].compareAndSet(PROCESSED, null)) {
                    // Success, our element has been added
                    return
                } else {
                    // Go to another round
                    continue
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

        var currentCell : Int? = null

        while (true)
        {
            if(combinerLock.compareAndSet(false, true)) { // acquired lock
                var res :E? = null
                if (currentCell == null) {
                    res = queue.removeFirstOrNull()
                } else {
                    if (tasksForCombiner[currentCell].value != PROCESSED) {
                        res = queue.removeFirstOrNull()
                    } else {
                        res = tasksForCombiner[currentCell].value as E?
                    }

                    cleanUp(currentCell)
                    currentCell = null
                }

                combinerPass()
                if (!combinerLock.compareAndSet(true, false))
                    throw Exception("Illegal mutex state")

                return res
            }

            else {
                if (currentCell == null) // null means there is no cell with our task
                {
                    currentCell = randomCellIndex()
                    if (!tasksForCombiner[currentCell].compareAndSet(null, DEQUE_TASK)) {
                        continue
                    } else {
                        currentCell = null
                        continue
                    }
                }


                if (tasksForCombiner[currentCell].compareAndSet(DEQUE_TASK, null)) {
                    // Go to another round
                    continue
                } else {
                    // Success, our element has been added
                    return tasksForCombiner[currentCell].getAndSet(null) as E?
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
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()