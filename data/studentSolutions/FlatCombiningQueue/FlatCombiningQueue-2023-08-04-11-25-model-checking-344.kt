package day4

import day1.Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun helpOthers() {
        require(combinerLock.value)

        for (i in 0..tasksForCombiner.size) {
            when (val task = tasksForCombiner[i].value) {
                Dequeue -> {
                    tasksForCombiner[i].value = ResultCell(queue.removeFirstOrNull())
                }

                is Enqueue<*> -> {
                    queue.addLast(task.element as E)
                    tasksForCombiner[i].value = ResultCell(task.element)
                }
            }
        }
    }

    override fun enqueue(element: E) {
        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element).also {
                helpOthers()
                combinerLock.value = false
            }
        } else {
            while (true) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, Enqueue(element))) {
                    while (true) {
                        when {
                            combinerLock.compareAndSet(false, true) -> {
                                queue.addLast(element).also {
                                    tasksForCombiner[index].value = null
                                    combinerLock.value = false
                                }
                                break
                            }

                            tasksForCombiner[index].value is ResultCell<*> -> {
                                tasksForCombiner[index].value = null
                                break
                            }
                        }
                    }
                    break
                }
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
            return queue.removeFirstOrNull().also {
                helpOthers()
                combinerLock.value = false
            }

        } else {
            while (true) {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, Dequeue)) {
                    while (true) {
                        if (combinerLock.compareAndSet(false, true)) {
                            return queue.removeFirstOrNull().also {
                                tasksForCombiner[index].value = null
                                combinerLock.value = false
                            }
                        }
                        val task = tasksForCombiner[index].value
                        if (task is ResultCell<*>) {
                            tasksForCombiner[index].value = null
                            return task.value as E
                        }
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

private class Enqueue<E>(val element: E)

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class ResultCell<V>(
    val value: V
)