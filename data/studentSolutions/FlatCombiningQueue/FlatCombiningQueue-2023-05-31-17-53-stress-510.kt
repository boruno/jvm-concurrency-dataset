package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    @Suppress("UNCHECKED_CAST")
    private fun combine() {
        for (ti in 0 until tasksForCombiner.size) {
            val task = tasksForCombiner[ti].value
            when (task) {
                DEQUE_TASK -> {
                    val deq = queue.removeFirstOrNull() ?: PROCESSED
                    tasksForCombiner[ti].compareAndSet(DEQUE_TASK, deq)
                }

                null -> continue
                PROCESSED -> continue
                else -> {
                    val enq = task as E
                    queue.addLast(enq)
                    tasksForCombiner[ti].compareAndSet(enq, PROCESSED)
                }
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
        var taskId: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, update = true)) {
                if (!(taskId != null && tasksForCombiner[taskId].getAndSet(null) == PROCESSED)) {
                    queue.addLast(element)
                }
                combine()
                combinerLock.getAndSet(false)
                return
            } else if (taskId == null) {
                taskId = randomCellIndex()
                tasksForCombiner[taskId].compareAndSet(null, element)
            } else {
                if (tasksForCombiner[taskId].compareAndSet(PROCESSED, null)) return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
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
        var taskId: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, update = true)) {
                var el: E? = null
                if (taskId != null) {
                    val taskRes = tasksForCombiner[taskId].getAndSet(null)
                    el = if (taskRes != null)
                        if (taskRes != PROCESSED) taskRes as E
                        else null
                    else queue.removeFirstOrNull()
                }
                else queue.removeFirstOrNull()
                combine()
                combinerLock.getAndSet(false)
                return el
            } else if (taskId == null) {
                taskId = randomCellIndex()
                tasksForCombiner[taskId].compareAndSet(null, DEQUE_TASK)
            } else {
                val taskRes = tasksForCombiner[taskId].getAndSet(null)
                if (taskRes != null)
                    return if (taskRes != PROCESSED) taskRes as E
                    else null
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