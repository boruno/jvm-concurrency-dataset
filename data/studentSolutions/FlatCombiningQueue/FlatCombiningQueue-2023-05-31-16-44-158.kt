//package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    fun combine() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value ?: continue

            if (task == DEQUE_TASK) {
                val removed = queue.removeFirstOrNull()
                tasksForCombiner[i].compareAndSet(DEQUE_TASK, removed)
            }
            else if (task != PROCESSED) {
                queue.addLast(task as E)
                tasksForCombiner[i].compareAndSet(task, PROCESSED)
            }
        }
    }

    override fun enqueue(element: E) {
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                queue.addLast(element)
                combine()
                combinerLock.compareAndSet(expect = true, update = false)
                return
            } else {
                for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
                    val cellNumber = randomCellIndex()
                    if (tasksForCombiner[cellNumber].compareAndSet(null, element)) {
                        repeat(TASKS_FOR_COMBINER_SIZE) {
                            if (tasksForCombiner[cellNumber].compareAndSet(PROCESSED, null)) {
                                return
                            }
                        }
                        //nobody took the task
                        break;
                    }
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                val removed = queue.removeFirstOrNull()
                combine()
                combinerLock.compareAndSet(expect = true, update = false)
                return removed
            } else {
                for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
                    val cellNumber = randomCellIndex()
                    if (tasksForCombiner[cellNumber].compareAndSet(null, DEQUE_TASK)) {
                        while (true) {
                            if (!tasksForCombiner[cellNumber].compareAndSet(DEQUE_TASK, DEQUE_TASK)) {
                                val removed = tasksForCombiner[cellNumber]
                                tasksForCombiner[cellNumber].compareAndSet(removed.value, null)
                                return removed.value as E?
                            }
                        }
                        //nobody took the task
                        break;
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
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()