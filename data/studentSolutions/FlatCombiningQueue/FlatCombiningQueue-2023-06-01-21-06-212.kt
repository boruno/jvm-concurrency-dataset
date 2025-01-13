//package day2

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
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to the element. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.


        while (true){
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                // got lock, perform adding
                queue.addLast(element)
                // help others
                help()
                // release lock
                combinerLock.compareAndSet(true, update = false)
            } else {
                val index = randomCellIndex()
                // create operation
                val operation = Operation(element)
                // try to get cell
                if (tasksForCombiner[index].compareAndSet(null, operation)) {
                    // now this is out cell - we need to release it
                    while (true) {
                        // got lock
                        if (combinerLock.compareAndSet(expect = false, update = true)) {
                            // get current operation status
                            val operationStatus = tasksForCombiner[index].value as Operation
                            // if nobody performed operation we need to perform it
                            if (operationStatus.state == null) queue.addLast(element)
                            // release the cell and help others
                            tasksForCombiner[index].compareAndSet(operationStatus, null)

                            help()
                            combinerLock.compareAndSet(true, update = false)
                            return
                        } else {
                            // we didn't get lock
                            // so let's check our progress in cell
                            val curValue = tasksForCombiner[index].value as Operation
                            if (curValue.state == PROCESSED) {
                                // if state is "PROCESSED" only we can change it to null
                                tasksForCombiner[index].getAndSet(null)
                                return
                            }
                        }
                    }
                }
            }
        }
    }


    private fun help() {
        // for each cell in array
        for (i in 0 until tasksForCombiner.size) {
            val cell = tasksForCombiner[i]
            val operation = cell.value as Operation?
            if (operation == null) continue
            // nobody can change state of operation except combiner
            if (operation.state == PROCESSED) continue
            if (operation.task == DEQUE_TASK) {
                val completedOperation = Operation(queue.removeFirstOrNull(), PROCESSED)
                cell.getAndSet(completedOperation)
            } else {
                queue.addLast(operation.task as E)
                val completedOperation = Operation(operation.task, PROCESSED)
                cell.getAndSet(completedOperation)
            }
        }
    }



        override fun dequeue(): E? {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to `DEQUE_TASK`. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
            while (true) {
                if (combinerLock.compareAndSet(expect = false, update = true)) {
                    // as combiner we have all rights to get value
                    // after perform regular operations
                    val value = queue.removeFirstOrNull()
                    help()
                    combinerLock.compareAndSet(true, update = false)
                    return value
                } else {
                    // try to get cell
                    val index = randomCellIndex()
                    val operation = Operation(DEQUE_TASK)
                    if (tasksForCombiner[index].compareAndSet(null, operation)) {
                        while (true) {
                            // we need to free this cell
                            if (combinerLock.compareAndSet(expect = false, update = true)) {
                                // we are combiner now
                                // check if other not performed operation
                                val curValue = tasksForCombiner[index].value as Operation
                                val returnedValue = if (curValue.state == null) queue.removeFirstOrNull() else curValue.task as E?
                                // release cell
                                tasksForCombiner[index].getAndSet(null)

                                help()
                                combinerLock.compareAndSet(true, update = false)
                                return returnedValue
                            } else {
                                // check our progress
                                val curValue = tasksForCombiner[index].value as Operation
                                if (curValue.state == PROCESSED) {
                                    tasksForCombiner[index].getAndSet(null)
                                    return curValue.task as E?
                                }
                            }
                        }
                    }
                }
            }
        }





    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private data class Operation(
        val task: Any?,
        val state: Any? = null
    )
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()

