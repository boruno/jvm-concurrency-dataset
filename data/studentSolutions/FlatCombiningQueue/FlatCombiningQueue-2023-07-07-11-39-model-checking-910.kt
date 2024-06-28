package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)


    fun processCombinerTasks(){
        for (index2 in 0 until TASKS_FOR_COMBINER_SIZE) {
            val cell = tasksForCombiner[index2].value
            when {
                (cell === null || cell is Result<*>) -> {}
                (cell === Dequeue) -> {
                    require(tasksForCombiner[index2].compareAndSet(Dequeue, queue.removeFirstOrNull()))
                }
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    queue.addLast(cell as E)
                }
            }
        }

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
        var elemInArray = false
        val index = randomCellIndex()
        var taskIsDone = false
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                if (elemInArray) {
                    val cellValue = tasksForCombiner[index].value
                    if (cellValue is Result<*>){
                        require(tasksForCombiner[index].compareAndSet(cellValue, null))
                        taskIsDone = true
                    } else {
                        require(tasksForCombiner[index].compareAndSet(element, null))
                    }
                }
                if (!taskIsDone){
                    queue.addLast(element)
                }
                processCombinerTasks()
                require(combinerLock.compareAndSet(expect = true, update = false))
                return
            } else {
                if (elemInArray) {
                    val cellValue = tasksForCombiner[index].value
                    if (cellValue is Result<*>) {
                        // we are done
                        require(tasksForCombiner[index].compareAndSet(cellValue, null))
                        return
                    }
                } else {
                    if (tasksForCombiner[index].compareAndSet(null, element)){
                        elemInArray = true
                    }
                }
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
        var elemInArray = false
        val index = randomCellIndex()
        var taskIsDone = false
        var result: E? = null
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                if (elemInArray) {
                    val cellValue = tasksForCombiner[index].value
                    if (cellValue is Result<*>){
                        require(tasksForCombiner[index].compareAndSet(cellValue, null))
                        result = cellValue.value as E?
                        taskIsDone = true
                    } else {
                        require(tasksForCombiner[index].compareAndSet(Dequeue, null))
                    }
                }
                if (!taskIsDone){
                    result = queue.removeFirstOrNull()
                }
                processCombinerTasks()
                require(combinerLock.compareAndSet(expect = true, update = false))
                return result
            } else {
                if (elemInArray) {
                    val cellValue = tasksForCombiner[index].value
                    if (cellValue is Result<*>) {
                        // we are done
                        require(tasksForCombiner[index].compareAndSet(cellValue, null))
                        return cellValue.value as E?
                    }
                } else {
                    if (tasksForCombiner[index].compareAndSet(null, Dequeue)){
                        elemInArray = true
                    }
                }
            }
        }



//        return queue.removeFirstOrNull()
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