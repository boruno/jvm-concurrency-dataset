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
        if (tryAcquireLock()) {
            enqueueIfAcquired(element)
            releaseLock()
        } else {
            val idx = announceOperation(element)
            while (true) {
                if (tryAcquireLock()) {
                    enqueueIfAcquired(element)
                    releaseLock()
                    break
                }
                if (tasksForCombiner[idx].compareAndSet(Result(element), null)) {
                    break
                }
            }
        }
    }

    fun tryAcquireLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    fun releaseLock() {
        combinerLock.getAndSet(false)
    }

    fun enqueueIfAcquired(element: E) {
        queue.addLast(element)
        go()
    }

    fun go() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value ?: continue
            if (task is Result<*>) continue
            if (task == Dequeue) {
                val res = queue.removeFirstOrNull()
                tasksForCombiner[i].compareAndSet(Dequeue, Result(res))
            } else {
                val value = tasksForCombiner[i].value as E
                queue.addLast(value)
                tasksForCombiner[i].compareAndSet(value, Result(value))
            }
        }
    }

    fun announceOperation(operation: Any?): Int {
        var idx = randomCellIndex()
        while (!tasksForCombiner[idx].compareAndSet(null, operation)) { idx = randomCellIndex()}
        return idx
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
        if (tryAcquireLock()) {
            val res = dequeueIfAcquired()
            releaseLock()
            return res
        } else {
            val idx = announceOperation(Dequeue)
            while (true) {
                if (tryAcquireLock()) {
                    val res = dequeueIfAcquired()
                    releaseLock()
                    return res
                }
                val cell = tasksForCombiner[idx].value
                if (cell is Result<*>) {
                    tasksForCombiner[idx].getAndSet(null)
                    return cell.value as E
                }
            }
        }
    }

    fun dequeueIfAcquired(): E? {
        val res = queue.removeFirstOrNull()
        go()
        return res
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