//package day4

import Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    fun tryLock() = combinerLock.compareAndSet(false, true)
    fun unlock() = combinerLock.compareAndSet(true, false)

    override fun enqueue(element: E) {
        val i = randomCellIndex()
        while (true) {
            when {
                tryLock() -> {
                    queue.addLast(element)
                    combine()
                    unlock()
                }

                else -> {
                    val res = Result(value = element)
                    when {
                        tasksForCombiner[i].compareAndSet(res, null) -> return
                        tasksForCombiner[i].compareAndSet(null, res) -> {}
                    }
                }
            }
        }
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
    }

    fun combine() {
        for (taskIndex in 0..TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[taskIndex].value
            when (task) {
                null, is Result<*> -> {}
                Dequeue -> {
                    queue.removeFirst()
                    tasksForCombiner[taskIndex].compareAndSet(task, Result(value = task))
                }

                else -> {
                    queue.addLast(task as E)
                    tasksForCombiner[taskIndex].compareAndSet(task, Result(value = task))
                }
            }
        }
    }

    override fun dequeue(): E? {
        val i = randomCellIndex()
        var result: E? = null
        while (true) {
            when {
                tryLock() -> {
                    result = queue.removeFirstOrNull()
                    combine()
                    unlock()
                    break
                }

                else -> {
                    val c = tasksForCombiner[i].value
                    when (c) {
                        is Result<*> -> {
                            result = c.value as E?
                            tasksForCombiner[i].compareAndSet(c, null)
                            break
                        }

                        null -> tasksForCombiner[i].compareAndSet(null, Dequeue)
                    }
                }
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
            }
        }
        return result
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