//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private val dequeueResults = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)


    override fun enqueue(element: E) {
        val ranIdx = randomCellIndex()
        while(true) {
            if (tasksForCombiner[ranIdx].compareAndSet(null, element)) {
                while (!tryLock()) {
                    if (tasksForCombiner[ranIdx].compareAndSet(Result, null)) { // wait till someone helps
                        return
                    }
                }
                if (tasksForCombiner[ranIdx].compareAndSet(element, null)) { // lock got, execute enqueue, help others
                    queue.addLast(element)
                    help()
                    return
                }
                tasksForCombiner[ranIdx].compareAndSet(Result, null) // clean cell in array
//                help()
                return
            }
        }
    }

    private fun help() {
        val idx = 0
        while (idx < tasksForCombiner.size) { // iterate the array
            val task = tasksForCombiner[idx].value
            if (task == null || task == Result) {
                continue
            }
            // case Dequeue
            if (task == Dequeue) {
                dequeueResults[idx].value = queue.removeFirstOrNull()
            }
            if (tasksForCombiner[idx].compareAndSet(Dequeue, Result)) {
                continue
            }
            // case Enqueue
            if (tasksForCombiner[idx].compareAndSet(task, Result)) {
                queue.addLast(task as E)
            }
        }
        unlock()
    }

    private fun tryLock() = combinerLock.compareAndSet(false, true)
    private fun unlock() { combinerLock.value = false }



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
        val ranIdx = randomCellIndex()
        while(true) {
            if (tasksForCombiner[ranIdx].compareAndSet(null, Dequeue)) { // set the random cell
                while (!tryLock()) { // can't get lock
                    if (tasksForCombiner[ranIdx].value == Result) { // wait for the result to appear
                        return (dequeueResults[ranIdx].value as E).also {
                            tasksForCombiner[ranIdx].compareAndSet(Result, null) // clear cell
                        }
                    }
                }
                if (tasksForCombiner[ranIdx].compareAndSet(Dequeue, null)) { // can get lock, so clear the cell
                    return queue.removeFirstOrNull().also {// execute operation and help others
                        help()
                    }
                }
                return (dequeueResults[ranIdx].value as E).also {
                    tasksForCombiner[ranIdx].compareAndSet(Result, null)
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
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)
