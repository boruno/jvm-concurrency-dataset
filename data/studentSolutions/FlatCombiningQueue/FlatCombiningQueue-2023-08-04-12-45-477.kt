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

        // queue.addLast(element)

        // 1.
        if (tryLock()) {
            // 2a.
            // Complete operation
            queue.addLast(element)

            // Help
            help()

            unlock()
        }
        else {
            // 2b.
            while (true) {
                val index = randomCellIndex()
                if (tryPutEnqueueOperation(index, element)) {
                    while (true) {
                        if (tryLock()) {
                            if (tasksForCombiner[index].value == element) {
                                queue.addLast(element)
                            }
                            tasksForCombiner[index].value = null
                            unlock()
                            return
                        }

                        if (tasksForCombiner[index].value is Result<*>) {
                            tasksForCombiner[index].value = null
                            return
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
            val value = queue.removeFirstOrNull()
            help()
            unlock()
            return value
        }
        else {
            while (true) {
                val index = randomCellIndex()
                if (tryPutDequeueOperation(index)) {
                    while (true) {
                        if (tryLock()) {
                            val cell = tasksForCombiner[index].value
                            val value =
                                if (cell == Dequeue) {
                                    queue.removeFirstOrNull()
                                }
                                else {
                                    (cell as Result<*>).value as E?
                                }

                            tasksForCombiner[index].value = null
                            unlock()
                            return value
                        }

                        val cell = tasksForCombiner[index].value
                        if (cell is Result<*>) {
                            tasksForCombiner[index].value = null
                            return cell.value as E?
                        }
                    }
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        combinerLock.value = false
    }

    private fun help() {
        for (index in 0 until TASKS_FOR_COMBINER_SIZE) {
            when (val value = tasksForCombiner[index].value) {
                Dequeue -> helpDequeue(index = index)
                null, is Result<*> -> { }
                else -> helpEnqueue(index = index, value = value as E)
            }
        }
    }

    private fun helpDequeue(index: Int) {
        val queueElement = queue.removeFirstOrNull()
        tasksForCombiner[index].value = Result(queueElement)
    }

    private fun helpEnqueue(index: Int, value: E) {
        queue.addLast(value)
        tasksForCombiner[index].value = Result(null)
    }

    private fun tryPutEnqueueOperation(index: Int, element: E): Boolean {
        return tasksForCombiner[index].compareAndSet(null, element)
    }

    private fun tryPutDequeueOperation(index: Int): Boolean {
        return tasksForCombiner[randomCellIndex()].compareAndSet(null, Dequeue)
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