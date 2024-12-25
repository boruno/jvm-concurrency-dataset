//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

@Suppress("UNCHECKED_CAST")
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

        val isCombiner = tryLock()
        if (isCombiner) {
            enqueueCombiner(element)
        } else {
            val index = announceTask(element!!)

            // wait for result
            while (true) {
                val curState = tasksForCombiner[index].value
                if (curState is Result<*>) {
                    if (tasksForCombiner[index].compareAndSet(curState, null)) {
                        return
                    }
                }
                if (tryLock()) {
                    enqueueCombiner(element)
                    return
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
        val isCombiner = tryLock()
        if (isCombiner) {
            val result = dequeueCombiner()
            unlock()
            return result
        } else {
            val index = announceTask(Dequeue)

            // wait for result
            while (true) {
                val curState = tasksForCombiner[index].value
                if (curState is Result<*>) {
                    if (tasksForCombiner[index].compareAndSet(curState, null)) {
                        return curState.value as E?
                    }
                }
                if (tryLock()) {
                    val result = dequeueCombiner()
                    unlock()
                    return result
                }
            }
        }
    }

    private fun announceTask(task: Any): Int {
        val index = randomCellIndex()
        while (true) {
            if (tasksForCombiner[index].compareAndSet(null, task)) {
                break
            }
        }
        return index
    }

    private fun enqueueCombiner(element: E) {
        queue.addLast(element)
        help()
    }

    private fun dequeueCombiner(): E? {
        val a = queue.removeFirstOrNull()
        help()
        return a
    }

    private fun help() {
        for (i in 0 until tasksForCombiner.size) {
            val task = tasksForCombiner[i].value
            when {
                task is Dequeue -> {
                    tasksForCombiner[i].compareAndSet(task, Result(dequeue()))
                }
                task is Result<*> || task == null -> {
                    continue
                }
                else -> {
                    enqueue(task as E)
                    tasksForCombiner[i].compareAndSet(task, Result(null))
                }
            }
        }
    }
    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun unlock() {
        combinerLock.getAndSet(false)
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)