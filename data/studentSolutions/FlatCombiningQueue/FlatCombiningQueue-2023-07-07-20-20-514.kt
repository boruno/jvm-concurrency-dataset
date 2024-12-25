//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        combinerLock.compareAndSet(expect = true, update = false)
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
        var isCombiner = false
        val cellIndex = randomCellIndex()
        var announced = false

        try {
            isCombiner = tryLock()

            if (isCombiner) {
                queue.addLast(element)
                return
            }

            announced = tasksForCombiner[cellIndex].compareAndSet(null, element)
            while (true) {
                if (announced) {
                    val value = tasksForCombiner[cellIndex].value
                    if (value is Result<*>) {
                        tasksForCombiner[cellIndex].compareAndSet(value, null)
                        return
                    }
                }
                if (!isCombiner) {
                    isCombiner = tryLock()
                    continue
                }
                if (isCombiner) {
                    queue.addLast(element)
                    return
                }
            }
        } finally {
            if (isCombiner) {
                if (announced) {
                    tasksForCombiner[cellIndex].value = null
                }
                traverseTasksForCombiner()
            }
            unlock()
        }
    }

    private fun add(element: E) {

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
        var isCombiner = false
        val cellIndex = randomCellIndex()
        var announced = false

        try {
            isCombiner = tryLock()

            if (isCombiner) {
                return queue.removeFirstOrNull()
            }

            announced = tasksForCombiner[cellIndex].compareAndSet(null, Dequeue)
            while (true) {
                if (announced) {
                    val value = tasksForCombiner[cellIndex].value
                    if (value is Result<*>) {
                        tasksForCombiner[cellIndex].compareAndSet(value, null)
                        return value.value as E?
                    }
                }
                if (!isCombiner) {
                    isCombiner = tryLock()
                    continue
                }
                if (isCombiner) {
                    return queue.removeFirstOrNull()
                }
            }
        } finally {
            if (isCombiner) {
                if (announced) {
                    tasksForCombiner[cellIndex].value = null
                }
                traverseTasksForCombiner()
            }
            unlock()
        }
    }

    private fun traverseTasksForCombiner() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val value = tasksForCombiner[i].value ?: continue
            if (value is Result<*>) continue
            if (value is Dequeue) {
                val result = Result(queue.removeFirstOrNull())
                tasksForCombiner[i].compareAndSet(value, result)
            } else {
                val result = Result(queue.addLast(value as E))
                tasksForCombiner[i].compareAndSet(value, result)
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