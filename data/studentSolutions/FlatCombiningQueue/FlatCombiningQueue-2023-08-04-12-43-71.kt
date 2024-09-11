package day4

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
        var addedToCombinerTasks = false
        val idx = randomCellIndex()
        while (true) {
            when {
                addedToCombinerTasks && tasksForCombiner[idx].value is Result<*> -> {
                    tasksForCombiner[idx].value = null
                    return
                }
                combinerLock.compareAndSet(false, true) -> {
                    queue.addLast(element)
                    tasksForCombiner[idx].value = Result
                    applyTasksForCombiner()
                    return
                }
                !addedToCombinerTasks -> addedToCombinerTasks = tasksForCombiner[idx].compareAndSet(null, element)
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
//        return queue.removeFirstOrNull()
        var addedToCombinerTasks = false
        val idx = randomCellIndex()

        while (true) {
            when {
                combinerLock.compareAndSet(expect = false, update = true) -> {
                    val element = if (addedToCombinerTasks) {
                        val curValue = tasksForCombiner[idx].value
                        if (curValue is Result<*>) curValue.value else queue.removeFirstOrNull()
                    } else queue.removeFirstOrNull()
                    applyTasksForCombiner()
                    return element as E?
                }
                addedToCombinerTasks -> {
                    val curValue = tasksForCombiner[idx].value
                    if (curValue is Result<*>) {
                        tasksForCombiner[idx].value = null
                        return curValue.value as E?
                    }
                }
                !addedToCombinerTasks -> addedToCombinerTasks = tasksForCombiner[idx].compareAndSet(null, Dequeue)
            }
        }
    }

    private fun applyTasksForCombiner() {
        for (idx in 0 until TASKS_FOR_COMBINER_SIZE) {
            when (val curValue = tasksForCombiner[idx].value) {
                null -> continue
                is Dequeue -> tasksForCombiner[idx].value = Result(queue.removeFirstOrNull())
                is Result<*> -> continue
                else /* is E? */ -> {
                    queue.addLast(curValue as E)
                    tasksForCombiner[idx].value = Result(curValue)
                }
            }
        }
        combinerLock.value = false
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