//package day2

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
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to the element. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                queue.addLast(element)
                processTasks()
                combinerLock.value = false
                break
            } else {
                val index = randomCellIndex()
                tasksForCombiner[index].compareAndSet(expect = null, update = element)
                while (tasksForCombiner[index].value != PROCESSED) {
                    if (combinerLock.compareAndSet(expect = false, update = true)) {
                        processTasks()
                        combinerLock.value = false
                        break
                    }
                }
                tasksForCombiner[index].value = null
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
                processTasks()
                combinerLock.value = false
                return result
            } else {
                val index = randomCellIndex()
                tasksForCombiner[index].compareAndSet(expect = null, update = DEQUE_TASK)
                while (tasksForCombiner[index].value != PROCESSED) {
                    if (combinerLock.compareAndSet(expect = false, update = true)) {
                        processTasks()
                        combinerLock.value = false
                        break
                    }
                }
                tasksForCombiner[index].value = null
            }
        }
    }

    private fun processTasks() {
        for (i in 0 until tasksForCombiner.size) {
            val task = tasksForCombiner[i].value
            if (task == DEQUE_TASK) {
                queue.removeFirstOrNull()
            } else {
                queue.addLast(task as E)
            }
            tasksForCombiner[i].value = PROCESSED
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