//package day4

import Queue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReferenceArray

@Suppress("UNCHECKED_CAST")
class FlatCombiningQueue<E : Any> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element)
            helpOthers()
            combinerLock.set(false)
            return
        }

        val index = announceTask(element)

        while (true) {
            if (tasksForCombiner.get(index) is Result<*>) {
                tasksForCombiner.set(index, null)
                return
            }
            if (combinerLock.compareAndSet(false, true)) {
                helpOthers()
                combinerLock.set(false)
            }
        }
    }

    override fun dequeue(): E? {
        if (combinerLock.compareAndSet(false, true)) {
            val removed = queue.removeFirstOrNull()
            helpOthers()
            combinerLock.set(false)
            return removed
        }

        val index = announceTask(Dequeue)

        while (true) {
            val task = tasksForCombiner.get(index)
            if (task is Result<*>) {
                tasksForCombiner.set(index, null)
                return task.value as E
            }
            if (combinerLock.compareAndSet(false, true)) {
                helpOthers()
                combinerLock.set(false)
            }
        }
    }

    private fun helpOthers() {
        for (i in 0 until tasksForCombiner.length()) {
            val task = tasksForCombiner[i] ?: continue
            when (task) {
                is Dequeue -> {
                    val dequeued = queue.removeFirstOrNull()
                    tasksForCombiner.set(i, Result(dequeued))
                }

                is Result<*> -> {}
                else -> {
                    queue.addLast(task as E)
                    tasksForCombiner.set(i, Result(task))
                }
            }
        }
    }

    private fun announceTask(task: Any): Int {
        while (true) {
            val index = randomCellIndex()
            val success = tasksForCombiner.compareAndSet(index, null, task)
            if (success) {
                return index
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