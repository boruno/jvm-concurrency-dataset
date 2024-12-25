//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        while (true) {
            if (tryLock()) {
                enqueueUnderLock(element)
                return
            }

            val index = randomCellIndex()
            if (tasksForCombiner[index].compareAndSet(null, element)) {
                while (true) {
                    if (tasksForCombiner[index].value != element)
                        return
                    if (tryLock()) {
                        if (tasksForCombiner[index].value != element) {
                            unlock()
                            return
                        }
                        enqueueUnderLock(element)
                        return
                    }
                }
            }
        }
    }

    private fun enqueueUnderLock(element: E) {
        helpUnderLock()
        queue.addLast(element)
        unlock()
    }

    override fun dequeue(): E? {
        while (true) {
            if (tryLock()) {
                return dequeueUnderLock()
            }

            val index = randomCellIndex()
            if (tasksForCombiner[index].compareAndSet(null, Dequeue)) {
                while (true) {
                    if (tasksForCombiner[index].value != Dequeue) {
                        val value = tasksForCombiner[index].value
                        tasksForCombiner[index].value = null
                        return value as E
                    }
                    if (tryLock()) {
                        if (tasksForCombiner[index].value != Dequeue) {
                            val value = tasksForCombiner[index].value
                            tasksForCombiner[index].value = null
                            unlock()
                            return value as E
                        }
                        return dequeueUnderLock()
                    }
                }
            }
        }
    }

    private fun dequeueUnderLock(): E? {
        helpUnderLock()
        val value = queue.removeFirstOrNull()
        unlock()
        return value
    }

    private fun helpUnderLock() {
        // Enqueue
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val value = tasksForCombiner[i].value
            if (value != null && value != Dequeue) {
                tasksForCombiner[i].value = null
                queue.addLast(value as E)
            }
        }

        // Dequeue
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val value = tasksForCombiner[i].value
            if (value == Dequeue) {
                val element = queue.removeFirstOrNull()
                tasksForCombiner[i].value = Result(element)
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private fun tryLock() = combinerLock.compareAndSet(false, true)
    private fun unlock() {
        combinerLock.value = false
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)