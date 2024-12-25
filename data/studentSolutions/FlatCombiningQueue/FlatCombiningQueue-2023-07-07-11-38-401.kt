//package day4

import day1.*
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
        if (combinerLock.compareAndSet(expect = false, update = true)) {
            enqueueUnderLock(element)
            unlock_()
            return
        }

        post(element!!) {
            enqueueUnderLock(element)
            unlock_()
        }
    }

    private fun <R> post(op : Any, underLock : () -> R?) : R? {
        var posted = false
        val randomIdx = randomCellIndex()
        while(true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                if (posted) {
                    check(tasksForCombiner[randomIdx].compareAndSet(op, null))
                }
                return underLock()
            }
            if (posted) {
                val mayBeResult = tasksForCombiner[randomIdx].value
                if (mayBeResult != op) {
                    check(tasksForCombiner[randomIdx].compareAndSet(mayBeResult, null))
                    return mayBeResult as R?
                }
            } else {
                posted = tasksForCombiner[randomIdx].compareAndSet(null, op)
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
        if (combinerLock.compareAndSet(expect = false, update = true)) {
            val result = dequeueUnderLock()
            unlock_()
            return result
        }

        return post(Dequeue) {
            dequeueUnderLock()
        }
    }

    private fun enqueueUnderLock(element: E) {
        queue.addLast(element)
        combineForOthers()
    }

    private fun dequeueUnderLock() : E? {
        val result = queue.removeFirstOrNull()
        combineForOthers()
        return result
    }

    private fun unlock_() {
        val mustBeTrue = combinerLock.compareAndSet(expect = true, update = false)
        check(mustBeTrue)
    }

    private fun combineForOthers() {
        check(combinerLock.value)
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val next = tasksForCombiner[i].value
            when (next) {
                null -> continue
                is Dequeue -> {
                    val result = Result(queue.removeFirstOrNull())
                    check(tasksForCombiner[i].compareAndSet(next, result))
                }
                else -> {
                    queue.addLast(next as E)
                    check(tasksForCombiner[i].compareAndSet(next, ReturnFromEnqueue))
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

private object ReturnFromEnqueue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)