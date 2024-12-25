//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun doWork(element: Any?): Any? {
        var index = -1
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                try {
                    if (index != -1) {
                        val maybeResult = tasksForCombiner[index].value
                        require(tasksForCombiner[index].compareAndSet(maybeResult, null))
                        if (maybeResult is Result<*>) {
                            helpOthers()
                            return maybeResult.value
                        }
                    }
                    // else
                    val result = doOperation(element)
                    helpOthers()
                    return result
                } finally {
                    require(combinerLock.compareAndSet(expect = true, update = false))
                }
            }
            else {
                when (index) {
                    -1 -> {
                        index = randomCellIndex()
                        if (!tasksForCombiner[index].compareAndSet(null, element)) {
                            index = -1
                        }
                    }
                    else -> {
                        val result = tasksForCombiner[index].value
                        if (result is Result<*>) {
                            require(tasksForCombiner[index].compareAndSet(result, null))
                            return result.value
                        }
                    }
                }
            }
        }
    }

    private fun doOperation(element: Any?): Any? {
        when (element) {
            Dequeue -> {
                return queue.removeFirstOrNull()
            }
            else -> {
                queue.addLast(element as E)
                return Unit
            }
        }
    }

    private fun helpOthers() {
        for (index in 0 until TASKS_FOR_COMBINER_SIZE) {
            val element = tasksForCombiner[index].value
            if (element != null) {
                val result = doOperation(element)
                require(tasksForCombiner[index].compareAndSet(element, Result(result)))
            }
        }
    }

    override fun enqueue(element: E) {
        doWork(element)
    }

    override fun dequeue(): E? {
        return doWork(Dequeue) as E?
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