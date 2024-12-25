//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    // private val dummy = Any()

    private fun traverseTaskForCombiner () {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            if (tasksForCombiner[i].value == null) { continue }
            val descr = tasksForCombiner[i].value as Descriptor<E>
            descr.state.compareAndSet(0, 1) // needed only for a bounded waiting!!!!
            if (descr.result ==  null) { // do dequeue
                descr.result = queue.removeFirstOrNull()

            } else { //do enqueue
                queue.addLast(descr.result as E)
            }
            descr.state.value = 2
        }
    }

    override fun enqueue(element: E) {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with the element. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        // queue.addLast(element)
        var isInArray = false
        var arrayIndex = -1
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                // check state in waiting array
                if (isInArray) {
                    val descr = tasksForCombiner[arrayIndex].value as Descriptor<E>
                    if (descr.state.value == 2) {
                        tasksForCombiner[arrayIndex].value = null
                        combinerLock.value = false
                        return
                    }
                    tasksForCombiner[arrayIndex].value = null
                }
                queue.addLast(element)
                traverseTaskForCombiner()
                combinerLock.value = false
                return
            } else if (isInArray) {
                val descr = tasksForCombiner[arrayIndex].value as Descriptor<E>
                if (descr.state.value == 2) {
                    tasksForCombiner[arrayIndex].value = null
                    return
                }
            } else {
                arrayIndex = randomCellIndex() % TASKS_FOR_COMBINER_SIZE
                val descr = Descriptor<E> (element)
                if (tasksForCombiner[arrayIndex].compareAndSet(null, descr)) {
                    isInArray = true
                }
            }
        }
    }

    override fun dequeue(): E? {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with `Dequeue`. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        // return queue.removeFirstOrNull()
        var isInArray = false
        var arrayIndex = -1
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                // check state in waiting array
                if (isInArray) {
                    val descr = tasksForCombiner[arrayIndex].value as Descriptor<E>
                    if (descr.state.value == 2) {
                        val res = descr.result
                        tasksForCombiner[arrayIndex].value = null
                        combinerLock.value = false
                        return res
                    }
                    tasksForCombiner[arrayIndex].value = null
                }
                val result = queue.removeFirstOrNull()
                traverseTaskForCombiner()
                combinerLock.value = false
                return result
            } else if (isInArray) {
                val descr = tasksForCombiner[arrayIndex].value as Descriptor<E>
                if (descr.state.value == 2) {
                    val res = descr.result
                    tasksForCombiner[arrayIndex].value = null
                    return res
                }
            } else {
                arrayIndex = randomCellIndex() % TASKS_FOR_COMBINER_SIZE
                val descr = Descriptor<E> (null)
                if (tasksForCombiner[arrayIndex].compareAndSet(null, descr)) {
                    isInArray = true
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!
private const val WAITING_TIME = 30

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

private class Descriptor<V> (
    public var result : V?,
    public val state : AtomicInt = atomic(0)
)

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)

// 0 1 2
enum class State {
    WAITING, INPROGRESS, DONE
}