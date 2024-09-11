package day4

import day1.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        op({ queue.addLast(element) }, { element as Any }, { })
    }

    override fun dequeue(): E? {
        return op({ queue.removeFirstOrNull() }, { Dequeue }, { it.value as E? })
    }

    private fun <T> op(block: () -> T, op: () -> Any, ex: (Result<*>) -> T): T {
        var cell = -1
        var combiner = false
        while (true) {
            if (!combiner) {
                combiner = combinerLock.compareAndSet(false, true)
            }
            if (combiner) {
                try {
                    if (cell != -1) {
                        val r = tasksForCombiner.getAndSet(cell, null)
                        if (r is Result<*>) {
                            tasksForCombiner.set(cell, null)
                            return ex(r)
                        }
                    }
                    val r = block()
                    ops()
                    return r
                } finally {
                    combinerLock.compareAndSet(true, false)
                }
            } else {
                if (cell == -1) {
                    val tryCell = randomCellIndex()
                    if (tasksForCombiner.compareAndSet(tryCell, null, op())) {
                        cell = tryCell
                    }
                } else {
                    val result = tasksForCombiner.get(cell)
                    if (result is Result<*>) {
                        tasksForCombiner.set(cell, null)
                        return ex(result)
                    }
                }
            }
        }
    }

    private fun ops() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            when (val op = tasksForCombiner.get(i)) {
                null -> continue
                is Result<*> -> continue
                Dequeue -> tasksForCombiner.set(i, Result(queue.removeFirstOrNull()))
                else -> {
                    queue.addLast(op as E)
                    tasksForCombiner.set(i, Result(Unit))
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