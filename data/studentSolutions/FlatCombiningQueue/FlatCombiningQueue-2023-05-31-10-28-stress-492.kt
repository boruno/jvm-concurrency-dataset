package day2

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
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to the element. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                help()
                queue.addLast(element)
                combinerLock.compareAndSet(true, update = false)
                return
            } else {
                val index = randomCellIndex()
                val operation = Operation(element)
                if (tasksForCombiner[index].compareAndSet(null, operation)) {
                    while (true) {
                        if (combinerLock.compareAndSet(expect = false, update = true)) {
                            // remove operation from stack
                            val curValue = tasksForCombiner[index].getAndSet(null) as Operation
                            val state = curValue.state.value

                            help()

                            if (state != PROCESSED) queue.addLast(element)
                            combinerLock.compareAndSet(true, update = false)
                            return
                        } else {
                            if (operation.state.compareAndSet(PROCESSED, null)) {
                                tasksForCombiner[index].getAndSet(null)
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    private fun help() {
        for (i in 0 until tasksForCombiner.size) {
            val cell = tasksForCombiner[i]
            val operation = cell.value as Operation?
            if (operation == null) continue
            val state = operation.state.value
            if (state == PROCESSED) continue
            if (operation.task == DEQUE_TASK) {
                if (operation.state.compareAndSet(null, PROCESSED)) {
                    operation.task = queue.removeFirstOrNull()
                }
            }
            else {
                if (operation.state.compareAndSet(null, PROCESSED)) {
                    queue.addLast(operation.task as E)
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
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to `DEQUE_TASK`. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                help()
                return queue.removeFirstOrNull().also {
                    combinerLock.compareAndSet(true, update = false)
                }
            } else {
                val index = randomCellIndex()
                val operation = Operation(DEQUE_TASK)
                if (tasksForCombiner[index].compareAndSet(null, operation)) {
                    while (true) {
                        if (combinerLock.compareAndSet(expect = false, update = true)) {
                            // remove operation from stack
                            val curValue = tasksForCombiner[index].getAndSet(null) as Operation
                            val state = curValue.state.value

                            help()

                            val returnedValue = if (state == null) queue.removeFirstOrNull() else curValue.task as E?
                            combinerLock.compareAndSet(true, update = false)
                            return returnedValue
                        } else {
                            if (operation.state.compareAndSet(PROCESSED, null)) {
                                val value = tasksForCombiner[index].getAndSet(null) as Operation
                                return value.task as E?
                            }
                        }
                    }
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private class Operation(
        var task: Any?
    ) {
        val state: AtomicRef<Any?> = atomic(null)
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()