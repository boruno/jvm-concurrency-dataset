package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private val valsForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private fun tryLock() = combinerLock.compareAndSet(false, update = true)
    private fun unlock() {
        combinerLock.value = false
    }

    private fun help() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val ithElem = tasksForCombiner[i].value
            if (ithElem == null || ithElem == PROCESSED) {
                continue
            }
            if (ithElem == DEQUE_TASK) {
                valsForCombiner[i].value = queue.removeFirstOrNull()
            }
            if (tasksForCombiner[i].compareAndSet(DEQUE_TASK, PROCESSED)) {
                continue
            }
            if (tasksForCombiner[i].compareAndSet(ithElem, PROCESSED)) {
                queue.addLast(ithElem as E)
            }
        }
        unlock()
    }

    override fun enqueue(element: E) {
        val rci = randomCellIndex()
        while(true) {
            if (tasksForCombiner[rci].compareAndSet(null, element)) {
                while (!tryLock()) {
                    if (tasksForCombiner[rci].compareAndSet(PROCESSED, null)) {
                        return
                    }
                }
                if (tasksForCombiner[rci].compareAndSet(element, null)) {
                    queue.addLast(element)
                    help()
                    return
                }
                tasksForCombiner[rci].compareAndSet(PROCESSED, null)
                help()
                return
            }
        }
    }

    override fun dequeue(): E? {
        val rci = randomCellIndex()
        var placed = false
        while(true) {
            /*if (tryLock()) {
                val curElem = tasksForCombiner[rci].value
                if (!placed) {
                    return queue.removeFirstOrNull().also {
                        help()
                    }
                }
                tasksForCombiner[rci].compareAndSet(DEQUE_TASK, null)

            }*/
            if (tasksForCombiner[rci].compareAndSet(null, DEQUE_TASK)) {
                while (!tryLock()) {
                    if (tasksForCombiner[rci].compareAndSet(PROCESSED, null)) {
                        return valsForCombiner[rci].value as E
                    }
                }
                if (tasksForCombiner[rci].compareAndSet(DEQUE_TASK, null)) {
                    return queue.removeFirstOrNull().also {
                        help()
                    }
                }
                return (valsForCombiner[rci].value as E).also {
                    tasksForCombiner[rci].compareAndSet(PROCESSED, null)
                    help()
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

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()