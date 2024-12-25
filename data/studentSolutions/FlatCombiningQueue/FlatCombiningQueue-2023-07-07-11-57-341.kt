//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

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
        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element)
            helpOthers()
            combinerLock.value = false
        } else {
            // put yur task in tasksForCombiner
            while (true) { // this loop is for retry
                val randomIdx = randomCellIndex()
                if (tasksForCombiner[randomIdx].compareAndSet(null, element)) {
                    while(true) {
                        // asked to enqueue the element -> expect Result(Unit) here
                        if (tasksForCombiner[randomIdx].value is Result<*>) {
                            return // if someone helped -> good, otherwise wait
                        }
                        if (combinerLock.compareAndSet(false, true)) {
                            // try to acquire lock by yourself
                            helpOthers()
                            combinerLock.value = false
                            return
                        }
                    }
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
        if (combinerLock.compareAndSet(false, true)) {
            val myResult = queue.removeFirstOrNull()
            helpOthers()
            combinerLock.value = false
            return myResult
        } else {
            // put yur task in tasksForCombiner
            while (true) { // this loop is for retry
                val randomIdx = randomCellIndex()
                if (tasksForCombiner[randomIdx].compareAndSet(null, Dequeue)) {
                    while(true) {
                        // asked to enqueue the element -> expect Result(Unit) here
                        if (tasksForCombiner[randomIdx].value is Result<*>) {
                            val value = (tasksForCombiner[randomIdx].value as Result<*>).value as E?
                            return value // if someone helped -> good, otherwise wait
                        }
                        if (combinerLock.compareAndSet(false, true)) {
                            // try to acquire lock by yourself
                            helpOthers()
                            // helped yourself too
                            val value = (tasksForCombiner[randomIdx].value as Result<*>).value
                            combinerLock.value = false
                            return value as E?
                        }
                    }
                }
            }
        }
    }

    private fun helpOthers() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value
            when (task){
                is Dequeue -> {
                    tasksForCombiner[i].value = Result(queue.removeFirstOrNull())
                }
                is Result<*> -> {}
                else -> {
                    if (task != null) {
                        val value = task as? E ?: error("Expected another type")
                        queue.addLast(value)
                        tasksForCombiner[i].value = Result(value) // completed enqueue
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
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)
