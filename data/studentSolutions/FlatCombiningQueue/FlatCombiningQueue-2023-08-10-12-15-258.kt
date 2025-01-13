//package day4

import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock() = combinerLock.compareAndSet(false, true)

    private fun unlock() {
        combinerLock.set(false)
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

        fun performDirectly() { queue.addLast(element) }

        if (tryLock()) {
            performDirectly()
            doCombinerJob()
            unlock()
            return
        }
        val randomIndex = randomCellIndex()
        val success = tasksForCombiner.compareAndSet(randomIndex, null, element)

        fun checkResult(): Boolean = success && tasksForCombiner[randomIndex] is Result<*>
        fun clean() { tasksForCombiner[randomIndex] = null }

        while (true) {
            if (checkResult()) {
                clean()
                return
            }
            if (tryLock()) {
                if (!checkResult()) {
                    performDirectly()
                }
                clean()
                unlock()
                return
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

        fun performDirectly(): E? = queue.removeFirstOrNull()

        if (tryLock()) {
            doCombinerJob()
            val element = performDirectly()
            unlock()
            return element
        }
        val randomIndex = randomCellIndex()
        val success = tasksForCombiner.compareAndSet(randomIndex, null, Dequeue)

        fun checkResult(): E? {
            if (!success) return null
            return (tasksForCombiner[randomIndex] as? Result<*>)?.value as E?
        }
        fun clean() { tasksForCombiner[randomIndex] = null }


        while (true) {
            val result = checkResult()
            if (result != null) return result
            if (tryLock()) {
                val element = checkResult() ?: performDirectly()
                clean()
                unlock()
                return element
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())


    private fun doCombinerJob() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i]
            if (task == Dequeue) {
                val element = queue.removeFirstOrNull()
                tasksForCombiner[i] = Result(element)
            } else if (task != null && task !is Result<*>) {
                queue.addLast(task as E)
                tasksForCombiner[i] = Result(ElementAdded)
            }
        }
    }

}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

private object ElementAdded

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)