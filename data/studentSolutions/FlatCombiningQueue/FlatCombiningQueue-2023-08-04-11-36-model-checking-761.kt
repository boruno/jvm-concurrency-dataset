package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)


    fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    fun unlock() {
        combinerLock.value = false
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

        if(tryLock()){
            try {
                queue.addLast(element)
                processOtherThreadsTasks()
            } finally {
                unlock()
            }
            return
        } else {
            var index = randomCellIndex()
            while(true){
                if(tasksForCombiner[index].compareAndSet(null, element)){
                    while(true){
                        if(tasksForCombiner[index].value is Result<*>){
                            tasksForCombiner[index].value = null
                            return
                        }
                        if(tryLock()){
                            try {
                                tasksForCombiner[index].value = null
                                queue.addLast(element)
                                processOtherThreadsTasks()
                                return
                            } finally {
                                unlock()
                            }
                        }
                    }
                } else {
                    index = randomCellIndex()
                }
            }
        }

    }

    private fun processOtherThreadsTasks() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value
            if (task is Dequeue) {
                val result = queue.removeFirstOrNull()
                tasksForCombiner[i].value = Result(result)
            } else if (task !is Result<*> && task != null) {
                queue.addLast(task as E)
                tasksForCombiner[i].value = Result(Unit)
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
        if(tryLock()){
            try {
                val result = queue.removeFirstOrNull()
                processOtherThreadsTasks()
                return result
            } finally {
                unlock()
            }
        } else {
            var index = randomCellIndex()
            while(true){
                if(tasksForCombiner[index].compareAndSet(null, Dequeue)){
                    while(true){
                        if(tasksForCombiner[index].value is Result<*>){
                            val result = tasksForCombiner[index].value as Result<E>
                            tasksForCombiner[index].value = null
                            return result.value
                        }
                        if(tryLock()){
                            try {
                                tasksForCombiner[index].value = null
                                val result = queue.removeFirstOrNull()
                                processOtherThreadsTasks()
                                return result
                            } finally {
                                unlock()
                            }
                        }
                    }
                } else {
                    index = randomCellIndex()
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