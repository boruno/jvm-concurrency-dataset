//package day4

import day1.Queue
import day4.Result
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReferenceArray

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        val cell = randomCellIndex()
        var hasCell = false
        while (true) {
            if (hasCell && tasksForCombiner.get(cell) == null) {
                // we did put value to enqueue and someone took it
                return
            }
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    if (hasCell) {
                        if (tasksForCombiner.get(cell) == null) {
                            // someone helped us
                            help()
                            return
                        } else {
                            require(tasksForCombiner.compareAndSet(cell, element, null)) {
                                "expected $element, got ${tasksForCombiner.get(cell)}"
                            }
                        }
                    }
                    queue.addLast(element)
                    help()
                    return
                }
                finally {
                    combinerLock.set(false)
                }
            }
            if (!hasCell) {
                hasCell = tasksForCombiner.compareAndSet(cell, null, element)
            }
        }
    }

    override fun dequeue(): E? {
        val cell = randomCellIndex()
        var hasCell = false
        while (true) {
            if (hasCell && tasksForCombiner.get(cell) is Result<*>) {
                return (tasksForCombiner.getAndSet(cell, null) as Result<*>).value as E
            }
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    if (hasCell) {
                        if (tasksForCombiner.get(cell) is Result<*>) {
                            // someone helped us
                            val result = (tasksForCombiner.getAndSet(cell, null) as Result<*>).value as E
                            help()
                            return result
                        } else {
                            // we asked, but no one helped us, our cell must contain Dequeue
                            assert(tasksForCombiner.compareAndSet(cell, Dequeue, null))
                        }
                    }
                    val result = queue.removeFirstOrNull()
                    help()
                    return result
                }
                finally {
                    combinerLock.set(false)
                }
            }
            if (!hasCell) {
                hasCell = tasksForCombiner.compareAndSet(cell, null, Dequeue)
            }
        }
    }

    private fun help() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner.get(i) ?: continue
            when (task) {
                is Result<*> -> {
                    // it should be taken by other thread
                    continue
                }
                is Dequeue -> {
                    assert(tasksForCombiner.compareAndSet(i, Dequeue, Result(queue.removeFirstOrNull())))
                    continue
                }
                else -> {
                    queue.addLast(task as E)
                    tasksForCombiner.set(i, null)
                }
            }
        }
    }

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