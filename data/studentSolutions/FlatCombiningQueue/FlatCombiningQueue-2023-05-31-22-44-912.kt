//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

private data class Operation(val state: Any?, val parameter: Any? = null)

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!


private val DEQUE_TASK = Any()

private val ENQUEUE_TASK = Any()

private val PROCESSED = Any()

private val DEQUE_OPERATION = Operation(DEQUE_TASK)

private val PROCESSED_OPERATION = Operation(PROCESSED)


class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Operation?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                queue.addLast(element)
                combine()
                combinerLock.compareAndSet(expect = true, update = false)
                return
            } else {
                val index = randomCellIndex()
                val cell = tasksForCombiner[index]
                val operation = Operation(ENQUEUE_TASK, element)
                if (cell.compareAndSet(null, operation)) {
                    while (true) {
                        if (combinerLock.compareAndSet(expect = false, update = true)) {
                            if (!cell.compareAndSet(PROCESSED_OPERATION, null)) {
                                queue.addLast(element)
                            }
                            cell.compareAndSet(operation, null)
                            combine()
                            combinerLock.compareAndSet(expect = true, update = false)
                            return
                        } else {
                            if (cell.compareAndSet(PROCESSED_OPERATION, null)) {
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    private fun combine() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val cell = tasksForCombiner[i]
            val operation = cell.value ?: continue
            if (operation.state == ENQUEUE_TASK) {
                queue.addLast(operation.parameter as E)
                cell.compareAndSet(operation, PROCESSED_OPERATION)
            } else if (operation.state == DEQUE_TASK) {
                val result = queue.removeFirstOrNull()
                cell.compareAndSet(operation, Operation(PROCESSED, result))
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                val result = queue.removeFirstOrNull()
                combine()
                combinerLock.compareAndSet(expect = true, update = false)
                return result
            } else {
                val index = randomCellIndex()
                val cell = tasksForCombiner[index]
                if (cell.compareAndSet(null, DEQUE_OPERATION)) {
                    while (true) {
                        if (combinerLock.compareAndSet(expect = false, update = true)) {
                            val operation = cell.value as Operation
                            val result =
                                (if (operation.state == PROCESSED) operation.parameter else operation.state) as E?
                            cell.compareAndSet(operation, null)
                            combine()
                            combinerLock.compareAndSet(expect = true, update = false)
                            return result
                        } else {
                            val operation = cell.value as Operation
                            if (operation.state == PROCESSED) {
                                cell.compareAndSet(operation, null)
                                return operation.parameter as E?
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



