//package day4

import Result
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun help() {
        require(combinerLock.get())
        for (index in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner.get(index) ?: continue
            if (task is Dequeue) {
                tasksForCombiner.set(index, Result(queue.removeFirstOrNull()))
            }
            if (task !is Dequeue && task !is Result<*>) {
                tasksForCombiner.set(index, Result(queue.addLast(task as E)))
            }
        }
        combinerLock.set(false)
    }

    override fun enqueue(element: E) {
        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element)
            help()
        } else {
            var index: Int
            do {
                index = randomCellIndex()
            } while (!tasksForCombiner.compareAndSet(index, null, element))
            var lockAquired = false
            while (true) {
                if (tasksForCombiner.get(index) is Result<*>) break
                lockAquired = combinerLock.compareAndSet(false, true)
                if (lockAquired) break
            }
            if (lockAquired) {
                if (tasksForCombiner.get(index) is Result<*>) {
                    tasksForCombiner.set(index, null)
                    combinerLock.set(false)
                    return
                }
                queue.addLast(element)
                help()
            } else {
                tasksForCombiner.set(index, null)
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
        if (combinerLock.compareAndSet(false, true)) {
            val res = queue.removeFirstOrNull()
            help()
            return res
        } else {
            var index: Int
            do {
                index = randomCellIndex()
            } while (!tasksForCombiner.compareAndSet(index, null, Dequeue))
            var lockAquired = false
            while (true) {
                if (tasksForCombiner.get(index) is Result<*>) break
                lockAquired = combinerLock.compareAndSet(false, true)
                if (lockAquired) break
            }
            if (lockAquired) {
                if (tasksForCombiner.get(index) is Result<*>) {
                    val res = tasksForCombiner.get(index) as Result<*>
                    tasksForCombiner.set(index, null)
                    combinerLock.set(false)
                    return res.value as E?
                }
                val res = queue.removeFirstOrNull()
                help()
                return res
            } else {
                val res = tasksForCombiner.get(index) as Result<*>
                tasksForCombiner.set(index, null)
                return res.value as E
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
        // TODO:      `null` with `Dequeue`. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)