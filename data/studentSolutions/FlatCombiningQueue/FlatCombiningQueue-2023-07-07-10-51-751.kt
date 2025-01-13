//package day4

import Queue
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E> : Queue<E> {
    // TODO: Make this code thread-safe using the flat-combining technique.
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        while (true) {
            // TODO: 1.  Try to become a combiner by
            // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
            if (combinerLock.tryLock()) {
                // TODO: 2a. On success, apply this operation and help others by traversing
                // TODO:     `tasksForCombiner`, performing the announced operations, and
                // TODO:      updating the corresponding cells to `Result`.
                queue.addLast(element)

                combinerTasks()
                combinerLock.unlock()
                return
            } else {
                // TODO: 2b. If the lock is already acquired, announce this operation in
                // TODO:     `tasksForCombiner` by replacing a random cell state from
                // TODO:      `null` with the element. Wait until either the cell state
                // TODO:      updates to `Result` (do not forget to clean it in this case),
                // TODO:      or `combinerLock` becomes available to acquire.
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, element)) {
                    while (true) {
                        val result = tasksForCombiner[index].value
                        if (result is Result<*>) {
                            tasksForCombiner[index].compareAndSet(result, null)
                            return
                        }
                    }
                }
            }
        }
    }

    private fun combinerTasks() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value
            when (task) {
                is Dequeue -> tasksForCombiner[i].compareAndSet(task, Result(queue.removeFirstOrNull()))
                is Result<*>, null -> continue
                else -> {
                    queue.addLast(task as E)
                    tasksForCombiner[i].compareAndSet(task, Result(task))
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

        while (true) {
            if (combinerLock.tryLock()) {
                val result = queue.removeFirstOrNull()

                combinerTasks()
                combinerLock.unlock()
                return result
            } else {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, Dequeue)) {
                    while (true) {
                        val result = tasksForCombiner[index].value
                        if (result is Result<*>) {
                            tasksForCombiner[index].compareAndSet(result, null)
                            return result as E
                        }
                    }
                }
            }
        }
    }

    private fun randomCellIndex(): Int = ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)

private inline fun AtomicBoolean.tryLock() = compareAndSet(expect = false, update = true)
private inline fun AtomicBoolean.unlock() = getAndSet(false)