package day4

import day1.*
import day4.Result
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock() = combinerLock.compareAndSet(false, true)

    private fun unLock() {
        combinerLock.value = false
    }

    private fun help() {
        for (idx in 0 until TASKS_FOR_COMBINER_SIZE) {
            when (val value = tasksForCombiner[idx].value) {
                Dequeue -> {
                    tasksForCombiner[idx].value = Result(queue.removeFirstOrNull())
                }
                null, Result -> continue
                else -> {
                    queue.addLast(value as E)
                    tasksForCombiner[idx].value = Result(null)
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
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with the element. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        // queue.addLast(element)

        if (tryLock()) {
            queue.addLast(element)
            help()
            unLock()
        } else {
            while (true) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, element)) {
                    while (true) {
                        if (tryLock()) {
                            if (tasksForCombiner[index].value == element) {
                                queue.addLast(element)
                            }
                            tasksForCombiner[index].value = null
                            help()
                            unLock()
                            return
                        } else {
                            val value = tasksForCombiner[index].value
                            if (value is Result<*>) {
                                tasksForCombiner[index].value = null
                                return
                            }
                        }
                    }
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
        // return queue.removeFirstOrNull()

        if (tryLock()) {
            val result = queue.removeFirstOrNull()
            help()
            unLock()
            return result
        } else {
            while (true) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, Dequeue)) {
                    while (true) {
                        if (tryLock()) {
                            val value = tasksForCombiner[index].value
                            val result = if (value == Dequeue) {
                                queue.removeFirstOrNull()
                            } else {
                                (value as Result<*>).value as E?
                            }
                            tasksForCombiner[index].value = null
                            help()
                            unLock()
                            return result
                        } else {
                            val value = tasksForCombiner[index].value
                            if (value is Result<*>) {
                                tasksForCombiner[index].value = null
                                return value.value as E
                            }
                        }
                    }
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