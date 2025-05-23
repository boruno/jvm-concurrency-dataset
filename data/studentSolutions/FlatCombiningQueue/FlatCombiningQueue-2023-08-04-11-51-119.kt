//package day4

import Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        combine({ queue.addLast(element) }, { element })
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? = combine({ queue.removeFirstOrNull() }, { Dequeue }) as E?

    private fun combine(operation: () -> Any?, operationDescriptor: () -> Any?): Any? {
        entry@ while (true) {
            // TODO: Make this code thread-safe using the flat-combining technique.
            // TODO: 1.  Try to become a combiner by
            // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
            if (combinerLock.compareAndSet(expect = false, update = true)) try {
                // TODO: 2a. On success, apply this operation
                val operationResult = operation()
                // TODO: and help others by traversing `tasksForCombiner`, performing the announced operations,
                for (i in 0 until tasksForCombiner.size) {
                    val task = tasksForCombiner[i].value ?: continue
                    @Suppress("UNCHECKED_CAST") val result = Result(
                        when (task) {
                            is Dequeue -> queue.firstOrNull()
                            (task as? E != null) -> queue.addLast(task as E)
                            else -> continue
                        }
                    )
                    // TODO: and updating the corresponding cells to `Result`.
                    tasksForCombiner[i].compareAndSet(task, result)
                }
                return operationResult
            } finally {
                check(combinerLock.compareAndSet(expect = true, update = false)) {
                    "Unable to release the lock, unable to make any progress"
                }
            } else while (true) {
                // TODO: 2b. If the lock is already acquired, announce this operation in
                // TODO:     `tasksForCombiner` by replacing a random cell state from
                // TODO:      `null` with `operationDescriptor`.
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, operationDescriptor())) {
                    // TODO: Wait until either
                    while (true) {
                        val value = tasksForCombiner[index].value
                        when {
                            // TODO: the cell state updates to `Result` (do not forget to clean it in this case),
                            value is Result<*> -> tasksForCombiner[index].compareAndSet(value, null)
                            // TODO: or `combinerLock` becomes available to acquire.
                            !combinerLock.value -> continue@entry
                            else -> continue
                        }
                    }
                } else continue
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