package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {

        var myCellIdx: Int
        while (true) {
            myCellIdx = randomCellIndex()
            if (tasksForCombiner[myCellIdx].compareAndSet(null, element))
                break
        }

        val myCell = tasksForCombiner[myCellIdx]
        while (true) {
            if (myCell.value == PROCESSED) {
                myCell.value = null
                return
            }

            if (combinerLock.compareAndSet(false, true))
                break
        }

        try {
            for (i in 0 until tasksForCombiner.size) {
                val c = tasksForCombiner[i]
                val value = c.value

                if (i == myCellIdx) {
                    if (value != PROCESSED)
                        queue.addLast(value as E)
                    c.value = null
                } else if (value == null || value == PROCESSED)
                    continue
                else if (value == DEQUE_TASK) {
                    c.value = queue.removeFirstOrNull()
                } else {
                    queue.addLast(value as E)
                    c.value = PROCESSED
                }
            }
        } finally {
            combinerLock.compareAndSet(true, false)
        }

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
//        queue.addLast(element)
    }


    override fun dequeue(): E? {

        var myCellIdx: Int
        while (true) {
            myCellIdx = randomCellIndex()
            if (tasksForCombiner[myCellIdx].compareAndSet(null, DEQUE_TASK))
                break
        }

        val myCell = tasksForCombiner[myCellIdx]
        while (true) {
            if (myCell.value != DEQUE_TASK) {
                return myCell.getAndSet(null) as E?
            }

            if (combinerLock.compareAndSet(false, true))
                break
        }

        try {
            var myResult: E? = null
            for (i in 0 until tasksForCombiner.size) {
                val c = tasksForCombiner[i]
                val value = c.value

                if (i == myCellIdx) {
                    myResult = if (value == DEQUE_TASK) queue.removeFirstOrNull() else value as E
                    c.value = null
                } else if (value == null || value == PROCESSED)
                    continue
                else if (value == DEQUE_TASK) {
                    val dequeued = queue.removeFirstOrNull()
                    c.value = dequeued
                } else {
                    queue.addLast(value as E)
                    c.value = PROCESSED
                }
            }

            return myResult
        } finally {
            combinerLock.compareAndSet(true, false)
        }

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
//        return queue.removeFirstOrNull()
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