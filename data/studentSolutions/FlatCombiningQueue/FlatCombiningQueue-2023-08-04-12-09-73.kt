//package day4

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
        var index: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                var shouldAddElement = true
                if (index != null) {
                    val oldValue = tasksForCombiner[index].getAndSet(null)
                    if (oldValue is Result<*>) {
                        shouldAddElement = false
                    }
                }
                if (shouldAddElement) {
                    queue.addLast(element)
                }
                for (i in 0 until tasksForCombiner.size) {
                    val task = tasksForCombiner[i].value ?: continue
                    when (task) {
                        is Dequeue -> tasksForCombiner[i].compareAndSet(task, Result(queue.removeFirstOrNull()))
                        is Result<*> -> continue
                        else -> {
                            queue.addLast(task as E)
                            tasksForCombiner[i].value = Result(null)
                        }
                    }
                }
                combinerLock.value = false
                break
            } else if (index == null) {
                val newIndex = randomCellIndex()
                if (tasksForCombiner[newIndex].compareAndSet(null, element)) {
                    index = newIndex
                }
            } else {
                if (tasksForCombiner[index].value is Result<*>) {
                    tasksForCombiner[index].value = null
                    break
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
        var index: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                var result: E? = null
                if (index != null) {
                    val oldValue = tasksForCombiner[index].getAndSet(null) as? Result<*>
                    if (oldValue != null) {
                        result = oldValue.value as E
                    }
                }
                if (result == null) {
                    result = queue.removeFirstOrNull()
                }
                for (i in 0 until tasksForCombiner.size) {
                    val task = tasksForCombiner[i].value ?: continue
                    when (task) {
                        is Dequeue -> tasksForCombiner[i].compareAndSet(task, Result(queue.removeFirstOrNull()))
                        is Result<*> -> continue
                        else -> {
                            queue.addLast(task as E)
                            tasksForCombiner[i].value = Result(null)
                        }
                    }
                }
                return result
            } else if (index == null) {
                val newIndex = randomCellIndex()
                if (tasksForCombiner[newIndex].compareAndSet(null, Dequeue)) {
                    index = newIndex
                }
            } else {
                val result = tasksForCombiner[index].value as? Result<*>
                if (result != null) {
                    tasksForCombiner[index].value = null
                    return result.value as E
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