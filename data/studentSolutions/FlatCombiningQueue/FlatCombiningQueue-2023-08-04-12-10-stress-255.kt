package day4

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
        val arrIdx = randomCellIndex()
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                if (tasksForCombiner[arrIdx].compareAndSet(Result(element),null)) { break}

                queue.addLast(element)
                for (i in 0..TASKS_FOR_COMBINER_SIZE-1) {
                    if (tasksForCombiner[i].value == Dequeue) {
                        tasksForCombiner[i].compareAndSet(Dequeue, queue.first())
                    }
                    if (tasksForCombiner[i].value != null && tasksForCombiner[i].value !is Result<*>) {
                        queue.addLast(tasksForCombiner[i].value as E)
                        tasksForCombiner[i].compareAndSet(tasksForCombiner[i].value, Result(tasksForCombiner[i].value))
                    }
                }
                break
            } else {
                if (tasksForCombiner[arrIdx].compareAndSet(null, element)) {
                    continue
                }
                if (tasksForCombiner[arrIdx].value is Result<*>) {
                    tasksForCombiner[arrIdx].compareAndSet(tasksForCombiner[arrIdx].value, null)
                    return
                }
            }
        }
        combinerLock.compareAndSet(true, false)
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
        val arrIdx = randomCellIndex()
        var result: E?
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                result = queue.removeFirstOrNull()
                for (i in 0..TASKS_FOR_COMBINER_SIZE-1) {
                    if (tasksForCombiner[i].value == Dequeue) {
                        tasksForCombiner[i].compareAndSet(Dequeue, Result(queue.removeFirstOrNull()))
                    }
                    if (tasksForCombiner[i].value != null && tasksForCombiner[i].value !is Result<*>) {
                        queue.addLast(tasksForCombiner[i].value as E)
                        tasksForCombiner[i].compareAndSet(tasksForCombiner[i].value, Result(tasksForCombiner[i].value))
                    }
                }
                combinerLock.compareAndSet(true, false)
                break
            }else{
                //lock is not free
                if (tasksForCombiner[arrIdx].value is Result<*>){
                    //return result from the cell
                    result = tasksForCombiner[arrIdx].value as E
                    tasksForCombiner[arrIdx].compareAndSet(result, null)
                    break
                }
                if (tasksForCombiner[arrIdx].value == null){
                    //put my task to the array
                    tasksForCombiner[arrIdx].compareAndSet(null, Dequeue)
                    continue
                }
            }
        }
        return result
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