//package day2

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
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to the element. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.

        var index: Int

        while (true) {
            index = randomCellIndex()
            if (tasksForCombiner[index].compareAndSet(null, element)) {
                break
            }
            if (becomeCombiner()) {

                queue.addLast(element)

                combine()
                return
            }
        }

        while(true) {
            if (becomeCombiner()) {
                tasksForCombiner[index].compareAndSet(element, null)

                queue.addLast(element)

                combine()
                break
            }
            if (tasksForCombiner[index].compareAndSet(PROCESSED, null)) {
                break
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
        var index: Int

        val res: E?
        while (true) {
            index = randomCellIndex()
            if (tasksForCombiner[index].compareAndSet(null, DEQUE_TASK)) {
                break
            }

            if (becomeCombiner()) {
                res = queue.removeFirstOrNull()

                combine()

                return res
            }
        }


        while(true) {
            if (becomeCombiner()) {
                tasksForCombiner[index].compareAndSet(DEQUE_TASK, null)
                res = queue.removeFirstOrNull()

                combine()

                break
            }
            val taskVal = tasksForCombiner[index].value
            if (taskVal is DequeueResult) {
                res = taskVal.element as E?
                tasksForCombiner[index].compareAndSet(taskVal, null)
                break
            }
        }

        return res
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private fun becomeCombiner(): Boolean {
        return combinerLock.compareAndSet(expect=false, update=true)
    }

    private fun combine() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            println("combine $i")
            val task = tasksForCombiner[i].value
            if (task == null || task == PROCESSED || task is DequeueResult) {
                continue
            } else if (task == DEQUE_TASK) {
                tasksForCombiner[i].compareAndSet(task, DequeueResult(queue.removeFirstOrNull()))
            } else  {
                queue.addLast(task as E)
                tasksForCombiner[i].compareAndSet(task, PROCESSED)
            }
        }
        combinerLock.compareAndSet(expect = true, update = false)
    }

}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()


@JvmInline
private value class DequeueResult(val element: Any?)