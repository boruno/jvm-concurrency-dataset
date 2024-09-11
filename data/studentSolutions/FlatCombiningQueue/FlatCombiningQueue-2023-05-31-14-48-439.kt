package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*
import kotlin.math.exp

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<State?>(TASKS_FOR_COMBINER_SIZE)

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
        if (combinerLock.compareAndSet(expect = false, update = true)) {
            queue.addLast(element)
            combine()
            combinerLock.compareAndSet(expect = true, update = false)
        } else {
            val request = State(ENQUEUE_TASK, element)
            while (!combinerLock.compareAndSet(expect = false, true)) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, request)) {
                    val value = tasksForCombiner[index].value
                    if (value?.operationState == PROCESSED) {
                        tasksForCombiner[index].compareAndSet(value, null)
                        return
                    }
                    tasksForCombiner[index].compareAndSet(request, null)
                }
            }
            queue.addLast(element)
            combine()
            combinerLock.compareAndSet(expect = true, update = false)
        }
    }

    private fun combine() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value
            if (task?.operationState == DEQUE_TASK) {
                val operationResult = queue.removeFirstOrNull()
                tasksForCombiner[i].compareAndSet(task, State(PROCESSED, operationResult))
            } else if (task?.operationState == ENQUEUE_TASK) {
                queue.add(task.operationResult as E)
                tasksForCombiner[i].compareAndSet(task, State(PROCESSED))
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
        if (combinerLock.compareAndSet(expect = false, update = true)) {
            val result = queue.removeFirstOrNull()
            combine()
            combinerLock.compareAndSet(expect = true, update = false)
            return result
        } else {
            val request = State(DEQUE_TASK)
            while(!combinerLock.compareAndSet(expect = false, update = true)) {
                val index = randomCellIndex()
                if(tasksForCombiner[index].compareAndSet(null, request)) {
                    val value = tasksForCombiner[index].value
                    if (value?.operationState == PROCESSED) {
                        tasksForCombiner[index].compareAndSet(value, null)
                        return value.operationResult as? E?
                    }
                    tasksForCombiner[index].compareAndSet(request, null)
                }
            }
            val result = queue.removeFirstOrNull()
            combine()
            combinerLock.compareAndSet(expect = true, update = false)
            return result
        }


    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private data class State(val operationState: Any?, val operationResult: Any? = null)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

private val ENQUEUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()