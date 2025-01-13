//package day4

import Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val lock = atomic(false) // unlocked initially
    private val tasks = atomicArrayOfNulls<Task<E>?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        execute(Enqueue(element))
    }

    override fun dequeue(): E? = execute(Dequeue())

    private fun execute(task: Task<E>): E? {
        check(task !is Return)
        var taskIndex = -1
        while (true) {
            // TODO: Make this code thread-safe using the flat-combining technique.
            // TODO: 1.  Try to become a combiner by
            // TODO:     changing `lock` from `false` (unlocked) to `true` (locked).
            if (lock.compareAndSet(expect = false, update = true)) try {
                // TODO: 2a. On success, apply this operation
                val result = (if (taskIndex >= 0) takeTask(taskIndex) else task).execute()
                // TODO: and help others by traversing `tasks`, performing the announced operations,
                executeTasks()
                return result
            } finally {
                check(lock.compareAndSet(expect = true, update = false)) {
                    "Unable to release the lock, unable to make any progress"
                }
            } else {
                // TODO: 2b. If the lock is already acquired, announce this operation in
                // TODO:     `tasks` by replacing a random cell state from
                // TODO:      `null` with `task`.
                val index = randomCellIndex()
                if (tasks[index].compareAndSet(null, task)) {
                    val result = waitForTask(index)
                    if (result != null) return result.execute()
                    taskIndex = index
                }
            }
        }
    }

    private fun takeTask(index: Int): Task<E> {
        val task = tasks[index].value ?: error("No task for index=$index")
        updateTasks(index, task, null)
        return task
    }

    private fun executeTasks() {
        for (index in 0 until tasks.size) {
            val task = tasks[index].value ?: continue
            val result = Return(
                when (task) {
                    is Enqueue, is Dequeue -> task.execute()
                    else -> continue
                }
            )
            // TODO: and updating the corresponding cells to `Return`.
            updateTasks(index, task, result)
        }
    }

    private fun waitForTask(index: Int): Return? {
        // TODO: Wait until either
        while (true) {
            val value = tasks[index].value
            when {
                // TODO: the cell state updates to `Return` (do not forget to clean it in this case),
                value is Return -> {
                    updateTasks(index, value, null)
                    return value
                }

                lock.value -> continue
                // TODO: or `lock` becomes available to acquire.
                else -> return null
            }
        }
    }

    private fun updateTasks(index: Int, expected: Task<E>?, update: Task<E>?) {
        check(tasks[index].compareAndSet(expected, update)) {
            "Expected value of task #$index  is not '$expected' but ${tasks[index].value}"
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasks.size)

    private sealed class Task<E> {
        abstract fun execute(): E?
    }

    private inner class Dequeue : Task<E>() {
        override fun execute() = queue.removeFirstOrNull()
    }

    private inner class Enqueue(val element: E) : Task<E>() {
        override fun execute(): E? {
            queue.addLast(element)
            return null
        }
    }

    private inner class Return(val element: E?) : Task<E>() {
        override fun execute() = element
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!