import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E: Comparable<E>> {
    private val queue = PriorityQueue<E>()
    private val operations = atomicArrayOfNulls<Operation<E>?>(OPERATION_ARRAY_SIZE)
    private val generator = Random()
    private val locked = atomic(false)

    private fun tryLock() = locked.compareAndSet(expect = false, update = true)
    private fun unlock() { locked.value = false }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var index = -1
        while (!tryLock()) {
            if (index < 0) index = try_create_operation(Operation(OperationTypes.POLL, null))
            if (index >= 0 && operations[index].value!!.type == OperationTypes.DONE) {
                return operations[index].getAndSet(null)!!.element
            }
        }
        solve_tasks()
        if (index >= 0) {
            unlock()
            return operations[index].getAndSet(null)!!.element
        }
        val v = queue.poll()
        unlock()
        return v
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var index = -1
        while (!tryLock()) {
            if (index < 0) index = try_create_operation(Operation(OperationTypes.PEEK, null))
            if (index >= 0 && operations[index].value!!.type == OperationTypes.DONE) {
                return operations[index].getAndSet(null)!!.element
            }
        }
        solve_tasks()
        if (index >= 0) {
            unlock()
            return operations[index].getAndSet(null)!!.element
        }
        val v = queue.peek()
        unlock()
        return v
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var index = -1
        while (!tryLock()) {
            if (index < 0) index = try_create_operation(Operation(OperationTypes.ADD, null))
            if (index >= 0 && operations[index].value!!.type == OperationTypes.DONE) {
                operations[index].getAndSet(null)
                return
            }
        }
        solve_tasks()
        if (index >= 0) {
            unlock()
            operations[index].getAndSet(null)
            return
        }
        queue.add(element)
        unlock()
    }

    private fun try_create_operation(op: Operation<E>): Int {
        val index = generator.nextInt(OPERATION_ARRAY_SIZE)
        if (operations[index].compareAndSet(null, op)) return index
        return -1
    }

    private fun solve_tasks() {
        for (index in 0 until OPERATION_ARRAY_SIZE) {
            val task = operations[index].value ?: continue
            when (task.type) {
                OperationTypes.DONE -> continue
                OperationTypes.POLL -> {
                    operations[index].value = Operation(OperationTypes.DONE, queue.poll())
                }
                OperationTypes.PEEK -> {
                    if (queue.isEmpty()) {
                        operations[index].value = Operation(OperationTypes.DONE, null)
                    } else {
                        operations[index].value = Operation(OperationTypes.DONE, queue.peek())
                    }
                }
                OperationTypes.ADD -> {
                    queue.add(task.element)
                    operations[index].value = Operation(OperationTypes.DONE, null)
                }
            }
        }
    }
}

enum class OperationTypes {
    POLL, PEEK, ADD, DONE
}
private class Operation<E>(val type: OperationTypes, val element: E?)
private const val OPERATION_ARRAY_SIZE = 10