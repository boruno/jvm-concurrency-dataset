package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)



    override fun enqueue(element: E) {

        if (combinerLock.compareAndSet(expect = false, update = true)) {
            queue.addLast(element)
            combinerRoutine()
            combinerLock.value = false

        } else {

            while(true) {
                val idx = randomCellIndex()
                val cell = tasksForCombiner[idx];

                if (cell.compareAndSet(null, element)) {

                    while (true) {
                        if (combinerLock.compareAndSet(expect = false, update = true)) {

                            if (cell.value !is Result<*>)
                                queue.addLast(element)

                            cell.value = null
                            combinerRoutine()
                            combinerLock.value = false
                            return;

                        }
                        else if (cell.value is Result<*>) {
                            cell.value = null;
                            return;
                        }
                    }

                }
//                else {
//                    while(true) {
//                        if (combinerLock.compareAndSet(expect = false, update = true)) {
//                            queue.addLast(element)
//                            combinerRoutine()
//                            combinerLock.value = false
//                            return
//                        }
//                    }
//                }
            }
        }


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
    }



    override fun dequeue(): E? {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).locked
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with `Dequeue`. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        if (combinerLock.compareAndSet(expect = false, update = true)) {
            val element = queue.removeFirstOrNull()
            combinerRoutine()
            combinerLock.value = false
            return element

        } else {

            while(true)
            {
                val idx = randomCellIndex()
                val cell = tasksForCombiner[idx];


                if (cell.compareAndSet(null, Dequeue)) {

                    while (true) {
                        if (combinerLock.compareAndSet(expect = false, update = true)) {

                            val result = if (cell.value is Result<*>) (cell.value as Result<*>).value  else queue.removeFirstOrNull()
                            cell.value = null;
                            combinerRoutine()
                            combinerLock.value = false
                            return result as E?
                        }
                        else {
                            if (cell.value is Result<*>) {
                                val result = cell.value as E?
                                cell.value = null;
                                return result;
                            }
                        }
                    }

                }
//                else {
//                    while(true) {
//                        if (combinerLock.compareAndSet(expect = false, update = true)) {
//                            val element = queue.removeFirstOrNull()
//                            combinerRoutine()
//                            combinerLock.value = false
//                            return element
//                        }
//                    }
//                }









            }



        }
    }

    private fun combinerRoutine() {
        var i = 0
        while (i < tasksForCombiner.size) {
            val cell = tasksForCombiner[i].value;

            if (cell is Dequeue) {
                tasksForCombiner[i].value = Result(queue.removeFirstOrNull())
            }
            else if (cell != null && cell !is Result<*>)
            {
                val elementToEnqueue = cell as E
                queue.addLast(elementToEnqueue)
                tasksForCombiner[i].value = Result(elementToEnqueue)
            }
            i++
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