//package day4

import Queue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReferenceArray

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

        fun doOperation() {
            queue.addLast(element)
            helpOthers()
            tryUnlock()
        }

        if (tryLock()) {
            doOperation()
        } else {
            var randomIndex: Int
            while (true) {
                randomIndex = randomCellIndex()
                if (tasksForCombiner.compareAndSet(randomIndex, null, element)) break
            }
            while (true) {
                val result = tasksForCombiner[randomIndex]
                if (result is Result<*>) {
                    if (tasksForCombiner.compareAndSet(randomIndex, result, null)) break
                }
                if (tryLock()) {
                    if (tasksForCombiner[randomIndex] !is Result<*>) {
                        doOperation()
                    }
                    tryUnlock()
                    break
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

        fun doOperation(): E? {
            val result = queue.removeFirstOrNull()
            helpOthers()
            tryUnlock()
            return result
        }

        if (tryLock()) {
            return doOperation()
        } else {
            var randomIndex: Int
            while (true) {
                randomIndex = randomCellIndex()
                if (tasksForCombiner.compareAndSet(randomIndex, null, Dequeue)) break
            }
            while (true) {
                val result = tasksForCombiner[randomIndex]
                if (result is Result<*>) {
                    if (tasksForCombiner.compareAndSet(randomIndex, result, null)) {
                        return result.value as E
                    }
                }
                if (tryLock()) {
                    if (tasksForCombiner[randomIndex] is Result<*>) {
                        val r = (tasksForCombiner[randomIndex] as Result<*>).value as E
                        tryUnlock()
                        return r
                    } else {
                        return doOperation()
                    }
                }
            }
        }
    }

    private fun helpOthers() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            when (val task = tasksForCombiner[i]) {
                null -> continue

                is Dequeue -> {
                    val result = queue.removeFirstOrNull()
                    tasksForCombiner.compareAndSet(i, task, Result(result))
                }

                else -> {
                    queue.addLast(task as E)
                    tasksForCombiner.compareAndSet(i, task, Result(task))
                }
            }
        }
    }

    private fun tryLock() = combinerLock.compareAndSet(false, true)

    private fun tryUnlock() = combinerLock.compareAndSet(true, false)

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