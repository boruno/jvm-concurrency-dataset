package day4

import day1.Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        mainLoop(element) {
            queue.addLast(element)
            Result(null)
        }
    }

    override fun dequeue(): E? {
        return mainLoop(Dequeue) {
            val element = queue.removeFirstOrNull()
            Result(element)
        }.value
    }

    private fun mainLoop(task: Any?, a: () -> Result<E?>): Result<E?> {
        while (true) {
            val res = tryCombine(a)
            if (res != null) {
                return res
            } else {
                val res = processViaTask(task, a)
                if (res != null) {
                    return res
                }
            }
        }
    }

    private fun processViaTask(task: Any?, a: () -> Result<E?>): Result<E?>? {
        val idx: Int = trySetToArray(task) ?: return null
        while (true) {
            val v = tasksForCombiner[idx].value
            if (v is Result<*>) {
                tasksForCombiner[idx].value = null
                return v as Result<E?>
            }
            val res = tryCombine(a, idx)
            if (res != null) {
                tasksForCombiner[idx].value = null
                return res
            }
        }
    }

    private fun trySetToArray(element: Any?): Int? {
        for (i in 0 until RETRIES_TO_FIND_CELL) {
            val curIndex = randomCellIndex()
            if (tasksForCombiner[curIndex].compareAndSet(null, element)) {
                return curIndex
            }
        }
        return null
    }

    private fun tryCombine(a: () -> Result<E?>, idx: Int? = null): Result<E?>? {
        if (combinerLock.compareAndSet(false, true)) {
            try {
                val res = if (idx == null) null else tasksForCombiner[idx].value
                if (res is Result<*>) {
                    return res as Result<E?>
                }
                combine()
                return a()
            } finally {
                combinerLock.value = false
            }
        }
        return null
    }

    private fun combine() {
        for (i in 0 until tasksForCombiner.size) {
            when (val v = tasksForCombiner[i].value) {
                Dequeue -> {
                    tasksForCombiner[i].value = Result(queue.removeFirstOrNull())
                }

                null, Result -> continue
                else -> {
                    queue.addLast(v as E)
                    tasksForCombiner[i].value = Result(v)
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!
private const val RETRIES_TO_FIND_CELL = 2

private object Dequeue

private class Result<V>(
    val value: V
)