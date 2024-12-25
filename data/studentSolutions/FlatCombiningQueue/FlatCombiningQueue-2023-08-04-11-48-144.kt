//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        if (combinerLock.compareAndSet(false, true)) {
            combinerEnq(element)
        }
        else {
            while (true) {
                val randomCellIndex = randomCellIndex()
                if (tasksForCombiner[randomCellIndex].compareAndSet(null, element)) {
                    while (true) {
                        val r = tasksForCombiner[randomCellIndex].value
                        if (r is Result<*>) {
                          tasksForCombiner[randomCellIndex].compareAndSet(r, null)
                          break
                        }
                    }
                    break
                }
                else if (combinerLock.compareAndSet(false, true)) {
                    combinerEnq(element)
                    return
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

    private fun combinerEnq(element: E) {
        queue.addLast(element)
        help()
        combinerLock.value = false
    }

    private fun help() {
        for (i in 0 until tasksForCombiner.size) {
            val op = tasksForCombiner[i].value
            if (op != null && op !is Result<*>) {
                if (op is Dequeue) {
                    tasksForCombiner[i].compareAndSet(op, Result(queue.removeFirstOrNull()))
                } else {
                    queue.addLast(op as E)
                    tasksForCombiner[i].compareAndSet(op, Result(null))
                }
            }
        }
    }

    override fun dequeue(): E? {
        var result: E? = null
        if (combinerLock.compareAndSet(false, true)) {
            return combinerDeq()
        }
        else {
            while (true) {
                val randomCellIndex = randomCellIndex()
                if (tasksForCombiner[randomCellIndex].compareAndSet(null, Dequeue)) {
                    while (true) {
                        val value = tasksForCombiner[randomCellIndex].value
                        if (value is Result<*>) {
                            tasksForCombiner[randomCellIndex].compareAndSet(value, null)
                            result = value.value as E
                            return result
                        }
                    }
                    break
                }
                else if (combinerLock.compareAndSet(false, true)) {
                    return combinerDeq()
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
        // TODO:      `null` with `Dequeue`. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        return null
    }

    private fun combinerDeq(): E? {
        val result = queue.removeFirstOrNull()
        help()
        combinerLock.value = false
        return result
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