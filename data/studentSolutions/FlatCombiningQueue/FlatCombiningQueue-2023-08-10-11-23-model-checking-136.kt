package day4

import day1.*
import day4.Result
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

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
        val lockAcqired = combinerLock.compareAndSet(false, true)
        if (lockAcqired) {
            queue.addLast(element)
            help()
            combinerLock.set(false)
        } else {
            while (true) {
                val cellId = randomCellIndex()
                if (tasksForCombiner.compareAndSet(cellId, null, element)) {
                    while (tasksForCombiner[cellId] !is Result<*>) {
                        val acquired = combinerLock.compareAndSet(false, true)
                        if (acquired && tasksForCombiner[cellId] !is Result<*>) {
                            help()
                            combinerLock.set(false)
                            break
                        }
                    }
                    tasksForCombiner[cellId] = null
                    break
                } else {
                    continue
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
        val lockAcqired = combinerLock.compareAndSet(false, true)
        var result: E? = null

        if (lockAcqired) {
            result = queue.removeFirstOrNull()
            help()
            combinerLock.set(false)
        } else {
            while (true) {
                val cellId = randomCellIndex()
                if (tasksForCombiner.compareAndSet(cellId, null, Dequeue)) {
                    while (tasksForCombiner[cellId] !is Result<*>) {
                        val acquired = combinerLock.compareAndSet(false, true)
                        if (acquired && tasksForCombiner[cellId] !is Result<*>) {
                            help()
                            result = (tasksForCombiner[cellId] as Result<E>).value
                            combinerLock.set(false)
                            break
                        }
                    }
                    tasksForCombiner[cellId] = null
                    break
                } else {
                    continue
                }
            }
        }
        return result
    }

    private fun help() {
        for (i in 0 until tasksForCombiner.length()) {
            val task = tasksForCombiner[i]
            task ?: continue
            if (task is Dequeue) {
                val result = queue.removeFirstOrNull()
                tasksForCombiner[i] = Result(result)
            } else {
                queue.addLast(task as E)
                tasksForCombiner[i] = Result(task)
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)