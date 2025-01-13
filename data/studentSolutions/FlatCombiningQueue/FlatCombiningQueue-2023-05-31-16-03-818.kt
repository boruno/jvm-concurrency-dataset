//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private val answersFromCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    @Suppress("UNCHECKED_CAST")
    private fun help() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val elem = tasksForCombiner[i].value ?: continue
            if (elem == PROCESSED) {
                continue
            } else if (elem == DEQUE_TASK) {
                answersFromCombiner[i].value = queue.removeFirstOrNull()
                tasksForCombiner[i].value = PROCESSED
            }
            else {
                queue.addLast(elem as E)
                tasksForCombiner[i].value = PROCESSED
            }
        }
    }

    override fun enqueue(element: E) {

        val cell = randomCellIndex()
        var placed = false
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                if (placed) {
                    tasksForCombiner[cell].compareAndSet(element, null)
                }
                queue.addLast(element)
                help()
                combinerLock.compareAndSet(expect = true, update = false)
                return
            }

            if (!placed && tasksForCombiner[cell].compareAndSet(null, element)) {
                placed = true
            } else {
                if (tasksForCombiner[cell].value == PROCESSED) {
                    tasksForCombiner[cell].compareAndSet(PROCESSED, null)
                    return
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        val cell = randomCellIndex()
        var placed = false
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                if (placed) {
                    tasksForCombiner[cell].compareAndSet(DEQUE_TASK, null)
                }
                val ans = queue.removeFirstOrNull()
                help()
                combinerLock.compareAndSet(expect = true, update = false)
                return ans
            }

            if (!placed && tasksForCombiner[cell].compareAndSet(null, DEQUE_TASK)) {
                placed = true
            } else {
                if (tasksForCombiner[cell].value == PROCESSED) {
                    val ans = answersFromCombiner[cell].value as E
                    tasksForCombiner[cell].compareAndSet(PROCESSED, null)
                    return ans
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
private val DEQUE_TASK = Any()

// TODO: Put this token in `answersFromCombiner` when the task is processed.
private val PROCESSED = Any()