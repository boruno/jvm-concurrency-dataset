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
                return extract_result(index)
            }
        }
        if (index >= 0) {
            if(operations[index].value!!.type == OperationTypes.DONE) {
                unlock()
                return extract_result(index)
            }
            extract_result(index)
        }
        val v = queue.poll()
        solve_tasks()
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
            if (index < 0) index = try_create_operation(Operation(OperationTypes.POLL, null))
            if (index >= 0 && operations[index].value!!.type == OperationTypes.DONE) {
                return extract_result(index)
            }
        }
        if (index >= 0) {
            if(operations[index].value!!.type == OperationTypes.DONE) {
                unlock()
                return extract_result(index)
            }
            extract_result(index)
        }
        val v = queue.peek()
        solve_tasks()
        unlock()
        return v
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var index = -1
        while (!tryLock()) {
            if (index < 0) index = try_create_operation(Operation(OperationTypes.POLL, null))
            if (index >= 0 && operations[index].value!!.type == OperationTypes.DONE) {
                extract_result(index)
                return
            }
        }
        if (index >= 0) {
            if(operations[index].value!!.type == OperationTypes.DONE) {
                unlock()
                extract_result(index)
                return
            }
            extract_result(index)
        }
        queue.add(element)
        solve_tasks()
        unlock()
    }

    private fun try_create_operation(op: Operation<E>): Int {
        val index = generator.nextInt(OPERATION_ARRAY_SIZE)
        if (operations[index].compareAndSet(null, op)) return index
        return -1
    }

    private fun extract_result(index: Int): E? {
        val operation = operations[index].getAndSet(null) ?: return null
        return operation.element
    }

    private fun solve_tasks() {
        for (index in 0 until OPERATION_ARRAY_SIZE) {
            val task = operations[index].value ?: continue
            when (task.type) {
                OperationTypes.DONE -> continue
                OperationTypes.POLL -> {
                    operations[index].compareAndSet(task, Operation(OperationTypes.DONE, queue.poll()))
                }
                OperationTypes.PEEK -> {
                    if (queue.isEmpty()) {
                        operations[index].compareAndSet(task, Operation(OperationTypes.DONE, null))
                    } else {
                        operations[index].compareAndSet(task, Operation(OperationTypes.DONE, queue.peek()))
                    }
                }
                OperationTypes.ADD -> {
                    queue.add(task.element)
                    operations[index].compareAndSet(task, Operation(OperationTypes.DONE, null))
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