//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private val valsForCombiner = arrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private fun tryLock() = combinerLock.compareAndSet(false, update = true)
    private fun unlock() {
        combinerLock.value = false
    }

    private fun help() {
        for (i in 0 until tasksForCombiner.size) {
            val ithElem = tasksForCombiner[i].value
            if (tasksForCombiner[i].compareAndSet(DEQUE_TASK, PROCESSED)) {
                valsForCombiner[i] = queue.removeFirstOrNull()
                continue
            }
            if (ithElem != null && ithElem != PROCESSED) {
                if (tasksForCombiner[i].compareAndSet(ithElem, PROCESSED)) {
                    queue.addLast(ithElem as E)
                }
            }
        }
        unlock()
    }

    override fun enqueue(element: E) {
        while(true) {
            if (tryLock()) {
                queue.addLast(element)
                help()
                return
            }
            val rci = randomCellIndex()
            if (tasksForCombiner[rci].compareAndSet(null, element)) {
                while (!tryLock()) {
                    if (tasksForCombiner[rci].compareAndSet(PROCESSED, null)) {
                        return
                    }
                }
                if (tasksForCombiner[rci].compareAndSet(element, null)) {
                    queue.addLast(element)
                }
                help()
                return
            }
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
        while(true) {
            if (tryLock()) {
                return queue.removeFirstOrNull().also {
                    help()
                }
            }
            val rci = randomCellIndex()
            if (tasksForCombiner[rci].compareAndSet(null, DEQUE_TASK)) {
                while (!tryLock()) {
                    if (tasksForCombiner[rci].compareAndSet(PROCESSED, null)) {
                        return valsForCombiner[rci] as E
                    }
                }
                if (tasksForCombiner[rci].compareAndSet(DEQUE_TASK, null)) {
                    return queue.removeFirstOrNull().also {
                        help()
                    }
                }
                return (valsForCombiner[rci] as E).also {
                    help()
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