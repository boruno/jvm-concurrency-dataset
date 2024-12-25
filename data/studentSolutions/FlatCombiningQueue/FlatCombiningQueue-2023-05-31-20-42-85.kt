//package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private val valueForCombiner = atomicArrayOfNulls<E?>(TASKS_FOR_COMBINER_SIZE)
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
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                queue.addLast(element)
                combine()
                combinerLock.compareAndSet(expect = true, update = false)
                return
            } else {
                val index = randomCellIndex()
                val stateCell = tasksForCombiner[index]
                val valueCell = tasksForCombiner[index]
                if (stateCell.compareAndSet(null, ENQUEUE_TASK) && valueCell.compareAndSet(null, element)) {
                    if (stateCell.compareAndSet(ENQUEUE_TASK, null) && valueCell.compareAndSet(element, null)) continue
                    valueCell.compareAndSet(element, null)
                    stateCell.compareAndSet(PROCESSED, null)
                    return
                }
            }
        }
    }

    private fun combine() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val stateCell = tasksForCombiner[i]
            val valueCell = valueForCombiner[i]
            val value = valueCell.value
            if (stateCell.compareAndSet(DEQUE_TASK, PROCESSED) && valueCell.compareAndSet(
                    null,
                    queue.removeFirstOrNull()
                )
            ) {
            } else if (stateCell.compareAndSet(ENQUEUE_TASK, PROCESSED) && valueCell.compareAndSet(value, null)) {
                queue.addLast(value as E)
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
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                val result = queue.removeFirstOrNull()
                combine()
                combinerLock.compareAndSet(expect = true, update = false)
                return result
            } else {
                val index = randomCellIndex()
                val stateCell = tasksForCombiner[index]
                val valueCell = valueForCombiner[index]
                if (stateCell.compareAndSet(null, DEQUE_TASK)) {
                    if (stateCell.compareAndSet(DEQUE_TASK, null)) continue
                    val value = valueCell.getAndSet(null)
                    tasksForCombiner[index].compareAndSet(value, null)
                    return value
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

private val ENQUEUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()