//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

//    private fun doDirtyWork() {
//        for (i in 0 until tasksForCombiner.size) {
//            when (val elem = tasksForCombiner[i].value) {
//                null, PROCESSED_ENQUEUE, PROCESSED_DEQUEUE_NULL -> {}
//                DEQUE_TASK -> {
//                    val res = queue.removeFirstOrNull()
//                    tasksForCombiner[i].value = res ?: PROCESSED_DEQUEUE_NULL
//                }
//                else -> {
//                    queue.addLast(elem as E)
//                    tasksForCombiner[i].value = PROCESSED_ENQUEUE
//                }
//            }
//        }
//    }

    override fun enqueue(element: E) {
        var i = randomCellIndex()
        var cellIsChosen = false

        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                if (!cellIsChosen) {
                    queue.addLast(element)
                    //doDirtyWork()
                    combinerLock.value = false
                    return
                }

                when (val prev = tasksForCombiner[i].value) {
                    null -> throw Exception("null enqueue lock")
                    DEQUE_TASK -> throw Exception("DEQUE_TASK enqueue lock")
                    PROCESSED_DEQUEUE_NULL -> throw Exception("PROCESSED_DEQUEUE_NULL enqueue lock")
                    PROCESSED_ENQUEUE -> {
                        tasksForCombiner[i].value = null
                        //doDirtyWork()
                        combinerLock.value = false
                        return
                    }
                    else -> throw Exception("found value enqueue lock")
                }
            } else {
                if (!cellIsChosen && !tasksForCombiner[i].compareAndSet(null, element)) {
                    i = randomCellIndex()
                    continue
                } else cellIsChosen = true

                when (val prev = tasksForCombiner[i].value) {
                    null -> throw Exception("null enqueue")
                    DEQUE_TASK -> throw Exception("DEQUE_TASK enqueue")
                    PROCESSED_DEQUEUE_NULL -> throw Exception("PROCESSED_DEQUEUE_NULL enqueue")
                    PROCESSED_ENQUEUE -> return
                    else -> {
                        if (prev == element)
                            continue
                        throw Exception("found value enqueue")
                    }
                }
            }
        }
    }

    override fun dequeue(): E? {
        var i = randomCellIndex()
        var cellIsChosen = false

        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                if (!cellIsChosen) {
                    val res = queue.removeFirstOrNull()
                    //doDirtyWork()
                    combinerLock.value = false
                    return res
                }

                when (val prev = tasksForCombiner[i].value) {
                    null -> throw Exception("null dequeue lock")
                    PROCESSED_ENQUEUE -> throw Exception("PROCESSED_ENQUEUE dequeue lock")
                    else -> {
                        tasksForCombiner[i].value = null

                        if (prev == DEQUE_TASK) {
                            combinerLock.value = false
                            return queue.removeFirstOrNull()
                        }

                        //doDirtyWork()
                        combinerLock.value = false
                        return if (prev == PROCESSED_DEQUEUE_NULL) null else prev as? E
                    }
                }
            } else {
                if (!cellIsChosen && !tasksForCombiner[i].compareAndSet(null, DEQUE_TASK)) {
                    i = randomCellIndex()
                    continue
                } else cellIsChosen = true

                when (val prev = tasksForCombiner[i].value) {
                    null -> throw Exception("null dequeue")
                    DEQUE_TASK -> continue
                    PROCESSED_ENQUEUE -> throw Exception("PROCESSED_ENQUEUE dequeue")
                    else -> {
                        tasksForCombiner[i].value = null
                        return if (prev == PROCESSED_DEQUEUE_NULL) null else prev as? E
                    }
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private val DEQUE_TASK = Any()

private val PROCESSED_ENQUEUE = Any()

private val PROCESSED_DEQUEUE_NULL = Any()