package day4

import day1.Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock() = combinerLock.compareAndSet(expect = false, update = true)
    private fun unlock() = combinerLock.compareAndSet(expect = true, update = false)

    private fun helpOthers() {
        for (taskIndex in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[taskIndex].value
            when (task) {
                null, is Result<*> -> {}
                Dequeue -> {
                    val result = queue.removeFirstOrNull()
                    tasksForCombiner[taskIndex].compareAndSet(task, Result(value = result))
                }

                else -> {
                    queue.addLast(task as E)
                    tasksForCombiner[taskIndex].compareAndSet(task, Result(value = task))
                }
            }
        }
    }

    override fun enqueue(element: E) {
        val i = randomCellIndex()
        val res = Result(value = element)
        while (true) {
            when {
                tryLock() -> {
                    queue.addLast(element)
                    tasksForCombiner[i].compareAndSet(res, null) // FIXME: is that needed?
                    helpOthers()
                    unlock()
                    return
                }
                tasksForCombiner[i].compareAndSet(res, null) -> return
                tasksForCombiner[i].compareAndSet(null, res) -> {}
            }
        }
    }

    override fun dequeue(): E? {
        val i = randomCellIndex()
        while (true) {
            when {
                tryLock() -> {
                    val result = queue.removeFirstOrNull()
                    helpOthers()
                    unlock()
                    return result
                }

                else -> {
                    val c = tasksForCombiner[i].value
                    when (c) {
                        is Result<*> -> {
                            val result = c.value as E?
                            tasksForCombiner[i].compareAndSet(c, null)
                            return result
                        }

                        null -> tasksForCombiner[i].compareAndSet(null, Dequeue)
                    }
                }
            }
        }
    }

    private fun randomCellIndex(): Int = ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private object Dequeue

private class Result<V>(
    val value: V
)