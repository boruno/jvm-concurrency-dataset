package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    fun becomeCombiner() = combinerLock.compareAndSet(expect = false, update = true)
    fun finishCombining() = combinerLock.compareAndSet(expect = true, update = false)
    fun doEnqOperation(myTaskIndex: Int, element: E) {
        val task = if (myTaskIndex == -1) null else tasksForCombiner[myTaskIndex].value
        if (task is Result<*>) {
            return
        } else {
            queue.addLast(element)
        }
    }

    fun doDeqOperation(myTaskIndex: Int): E? {
        val task = if (myTaskIndex == -1) null else tasksForCombiner[myTaskIndex].value
        return if (task is Result<*>) {
            task.value as E?
        } else {
            val result = queue.removeFirstOrNull()
            result
        }
    }

    fun removeTaskIfNeeded(myTaskIndex: Int) {
        val task = if (myTaskIndex == -1) null else tasksForCombiner[myTaskIndex].value
        if (task != null) {
            tasksForCombiner[myTaskIndex].compareAndSet(task, null)
        }
    }

    fun isTaskAdded(myTaskIndex: Int) = myTaskIndex != -1

    fun tryGetResultTask(myTaskIndex: Int): Any? {
        if (myTaskIndex == -1) return null
        val myTask = tasksForCombiner[myTaskIndex].value
        if (myTask is Result<*>) {
            return myTask.value
        }

        return null
    }

    private fun addTaskToCombiner(task: Any?): Int {
        val taskIndex = randomCellIndex()
        return if (tasksForCombiner[taskIndex].compareAndSet(null, task)) {
            taskIndex
        } else {
            -1
        }
    }

    override fun enqueue(element: E) {
        var myTaskIndex: Int = -1
        while (true) {
            if (becomeCombiner()) {
//                return try {
                doEnqOperation(myTaskIndex, element)
                removeTaskIfNeeded(myTaskIndex)
                doCombine()
//                } finally {
                finishCombining()
//                }
            }

            if (!isTaskAdded(myTaskIndex)) {
                myTaskIndex = addTaskToCombiner(element)
                continue
            }

            val result = tryGetResultTask(myTaskIndex)
            if (result != null) {
                removeTaskIfNeeded(myTaskIndex)
                return
            }
        }
    }

    private fun doCombine() {
        // traverse array
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val cellValue = tasksForCombiner[i].value
            when (cellValue) {
                null, is Result<*> -> continue
                is Dequeue -> {
                    tasksForCombiner[i].compareAndSet(cellValue, Result(queue.firstOrNull()))
                    queue.removeFirstOrNull()
                }

                else -> {
                    val result = Result(cellValue as E)
                    tasksForCombiner[i].compareAndSet(cellValue, result)
                    queue.addLast(cellValue as E)
                }
            }
        }
    }

    override fun dequeue(): E? {
        var myTaskIndex: Int = -1
        while (true) {
            if (becomeCombiner()) {
//                return try {
                val element = doDeqOperation(myTaskIndex)
                removeTaskIfNeeded(myTaskIndex)
                doCombine()
                element
//                } finally {
                finishCombining()
//                }
            }

            if (!isTaskAdded(myTaskIndex)) {
                myTaskIndex = addTaskToCombiner(Dequeue)
                continue
            }

            val result = tryGetResultTask(myTaskIndex)
            if (result != null) {
                removeTaskIfNeeded(myTaskIndex)
                return result as E?
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