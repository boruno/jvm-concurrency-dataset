//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    fun lock(): Boolean {
        return combinerLock.compareAndSet(expect = false, update = true)
    }

    fun unlock() {
        combinerLock.compareAndSet(expect = true, update = false)
    }

    override fun enqueue(element: E) {
        if (lock()) {
            queue.addLast(element)
            for (i in 0 until tasksForCombiner.size) {
                when (val task = tasksForCombiner[i].value) {
                    is Dequeue -> {
                        val toAdd = Result(dequeue())
                        tasksForCombiner[i].compareAndSet(Dequeue, toAdd)
                    }
                    null -> continue
                    else -> {
                        queue.addLast(task as E)
                        tasksForCombiner[i].compareAndSet(task, Result(null))
                    }
                }
            }
            unlock()
            return
        } else {
            while (true) {
                val randomCellIndex = randomCellIndex()
                if (tasksForCombiner[randomCellIndex].compareAndSet(null, element)) {
                    while (true) {
                        if (tasksForCombiner[randomCellIndex].value is Result<*>) {
                            return
                        } else if (lock()) {
                            queue.addLast(element)
                            for (i in 0 until tasksForCombiner.size) {
                                when (val task = tasksForCombiner[i].value) {
                                    is Result<*> -> {
                                        tasksForCombiner[i].compareAndSet(task, null)
                                    }
                                    is Dequeue -> {
                                        val toAdd = Result(dequeue())
                                        tasksForCombiner[i].compareAndSet(Dequeue, toAdd)
                                    }
                                    null -> continue
                                    else -> {
                                        queue.addLast(task as E)
                                        tasksForCombiner[i].compareAndSet(task, Result(null))
                                    }
                                }
                            }
                            unlock()
                            return
                        }
                    }
                }
            }
        }
    }

    override fun dequeue(): E? {
        if (lock()) {
            val res = queue.removeFirstOrNull()
            for (i in 0 until tasksForCombiner.size) {
                when (val task = tasksForCombiner[i].value) {
                    is Dequeue -> {
                        val toAdd = Result(dequeue())
                        tasksForCombiner[i].compareAndSet(Dequeue, toAdd)
                    }
                    null -> continue
                    else -> {
                        queue.addLast(task as E)
                        tasksForCombiner[i].compareAndSet(task, Result(null))
                    }
                }
            }
            unlock()
            return res
        } else {
            while (true) {
                val randomCellIndex = randomCellIndex()
                if (tasksForCombiner[randomCellIndex].compareAndSet(null, Dequeue)) {
                    while (true) {
                        if (tasksForCombiner[randomCellIndex].value is Result<*>) {
                            return (tasksForCombiner[randomCellIndex].value as Result<*>).value as E
                        } else if (lock()) {
                            var res = queue.removeFirstOrNull()
                            for (i in 0 until tasksForCombiner.size) {
                                when (val task = tasksForCombiner[i].value) {
                                    is Result<*> -> {
                                        res = task.value as E
                                        tasksForCombiner[i].compareAndSet(task, null)
                                    }
                                    is Dequeue -> {
                                        val toAdd = Result(dequeue())
                                        tasksForCombiner[i].compareAndSet(Dequeue, toAdd)
                                    }

                                    null -> continue
                                    else -> {
                                        queue.addLast(task as E)
                                        tasksForCombiner[i].compareAndSet(task, Result(null))
                                    }
                                }
                            }
                            unlock()
                            return res
                        }
                    }
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

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)