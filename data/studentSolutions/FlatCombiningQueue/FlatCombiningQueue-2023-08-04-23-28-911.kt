//package day4

import day1.Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E : Any> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        perform(element) {
            queue.addLast(element)
        }
    }

    override fun dequeue(): E? {
        @Suppress("UNCHECKED_CAST")
        return perform(Dequeue) {
            queue.removeFirstOrNull()
        } as E?
    }

    private fun perform(descriptor: Any, operation: () -> Any?): Any? {
        var index = -1
        while (true) {
            // TODO: Make this code thread-safe using the flat-combining technique.
            // TODO: 1.  Try to become a combiner by
            // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
            return if (combinerLock.compareAndSet(expect = false, update = true)) try {
                combine {
                    if (index > -1) {
                        val task = tasksForCombiner[index].value
                        checkNotNull(task) {
                            "No task for index=$index"
                        }
                        check(tasksForCombiner[index].compareAndSet(task, null)) {
                            "Combiner task #$index expected value is not '$task' but got ${tasksForCombiner[index].value}"
                        }
                        @Suppress("UNCHECKED_CAST")
                        when {
                            task is Result<*> -> task.value
                            task is Dequeue -> queue.removeFirstOrNull()
                            (task as? E != null) -> queue.addLast(task)
                            else -> error("Unexpected task '$task' type '${task::class.java.canonicalName}'")
                        }
                    } else operation()
                }
            } finally {
                check(combinerLock.compareAndSet(expect = true, update = false)) {
                    "Unable to release the lock, unable to make any progress"
                }
            } else {
                val (i, result) = addCombinerTaskAndWait(descriptor)
                index = i
                if (result == null) continue
                result.value
            }
        }
    }

    private fun combine(operation: () -> Any?): Any? {
        // TODO: 2a. On success, apply this operation
        val operationResult = operation()
        // TODO: and help others by traversing `tasksForCombiner`, performing the announced operations,
        for (i in 0 until tasksForCombiner.size) {
            val task = tasksForCombiner[i].value ?: continue
            val result = Result(
                @Suppress("UNCHECKED_CAST") when (task) {
                    is Dequeue -> queue.removeFirstOrNull()
                    (task as? E != null) -> queue.addLast(task as E)
                    else -> continue
                }
            )
            // TODO: and updating the corresponding cells to `Result`.
            check(tasksForCombiner[i].compareAndSet(task, result)) {
                "Combiner task #$i expected value is not '$task' but got ${tasksForCombiner[i].value}"
            }
        }
        return operationResult
    }

    private fun addCombinerTaskAndWait(combinerTask: Any): Pair<Int, Result<Any?>?> {
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with `operationDescriptor`.
        val index = randomCellIndex()
        if (tasksForCombiner[index].compareAndSet(null, combinerTask)) {
            // TODO: Wait until either
            while (true) {
                val value = tasksForCombiner[index].value
                @Suppress("UNCHECKED_CAST")
                when {
                    // TODO: the cell state updates to `Result` (do not forget to clean it in this case),
                    value as? Result<Any?> != null -> {
                        check(tasksForCombiner[index].compareAndSet(value, null)) {
                            "Combiner task #$index expected value is not '$value' but got ${tasksForCombiner[index].value}"
                        }
                        return -1 to value
                    }
                    // TODO: or `combinerLock` becomes available to acquire.
                    !combinerLock.value -> return index to null
                    else -> continue
                }
            }
        } else return -1 to null
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