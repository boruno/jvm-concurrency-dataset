//package day4

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        while (true) {
            if (tryLock()) {
                enqueueAndProcess(element)
            }
            else {
                while (true) {
                    val index = randomCellIndex()
                    if (tasksForCombiner[index].compareAndSet(null, element)) {
                        while (true) {
                            if (tryLock()) {
                                tasksForCombiner[index].value = null

                                enqueueAndProcess(element)
                                return
                            }
                            else if (tasksForCombiner[index].value is Result<*>) {
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
        while (true) {
            if (tryLock()) {
                return dequeueAndProcess()
            } else {
                while (true) {
                    val index = randomCellIndex()
                    if (tasksForCombiner[index].compareAndSet(null, Dequeue)) {
                        while (true) {
                            if (tryLock()) {
                                tasksForCombiner[index].value = null
                                return dequeueAndProcess()
                            } else if (tasksForCombiner[index].value is Result<*>) {
                                val result = (tasksForCombiner[index].value as Result<*>).value
                                tasksForCombiner[index].value = null
                                @Suppress("UNCHECKED_CAST")
                                return result as? E
                            }
                        }
                    }
                }
            }
        }
    }

    private fun tryLock() = combinerLock.compareAndSet(expect = false, update = true)

    private fun dequeueAndProcess(): E? {
        val result = queue.removeFirstOrNull()

        try {
            processTasks()
            return result
        } finally {
            combinerLock.value = false
        }
    }

    private fun enqueueAndProcess(element: E) {
        queue.addLast(element)

        try {
            processTasks()
            return
        } finally {
            combinerLock.value = false
        }
    }

    private fun processTasks() {
        for (index in 0 until tasksForCombiner.size) {
            when (tasksForCombiner[index].value) {
                is Dequeue -> {
                    val res = queue.removeFirstOrNull()
                    tasksForCombiner[index].value = Result(res)
                }

                is Result<*> -> {
                    continue
                }

                is Any -> {
                    @Suppress("UNCHECKED_CAST")
                    queue.addLast(tasksForCombiner[index].value as E)
                    tasksForCombiner[index].value = Result(null)
                }

                else -> continue
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
    val value: V?
)