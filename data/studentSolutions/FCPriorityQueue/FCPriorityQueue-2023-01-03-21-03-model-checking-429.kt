import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.ReentrantLock
import kotlin.random.Random
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private enum class OperationStatus {
        POLL, PEEK, ADD, DONE
    }

    private class Operation<E>(val status: OperationStatus, val element: E? = null)

    private val q = PriorityQueue<E>()
    private val flatCombiningLock = ReentrantLock()
    private val flatCombiningArray = atomicArrayOfNulls<Operation<E>>(10)

    private fun flatCombining(command: () -> Any, operation: Operation<E>): E? {
        while (true) {
            val index = Random.nextInt(flatCombiningArray.size)
            if (flatCombiningLock.tryLock()) {
                val result = command()
                combineOperations()
                flatCombiningLock.unlock()
                return result as E?
            }
            val currentOperation = flatCombiningArray[index].value
            if (currentOperation != null) {
                if (currentOperation.status == OperationStatus.DONE) {
                    flatCombiningArray[index].compareAndSet(currentOperation, null)
                    return currentOperation.element
                }
            }
        }

//        if (flatCombiningLock.tryLock()) {
//            val result = command()
//            combineOperations()
//            flatCombiningLock.unlock()
//            return result as E?
//        } else {
//            while (true) {
//                val index = Random.nextInt(flatCombiningArray.size)
//                while (true) {
//                    if (flatCombiningArray[index].compareAndSet(null, operation)) {
//                        break
//                    }
//                }
//                val currentOperation = flatCombiningArray[index].value
//                if (currentOperation != null) {
//                    if (currentOperation.status == OperationStatus.DONE) {
//                        if (flatCombiningArray[index].compareAndSet(currentOperation, null)) {
//                            return currentOperation.element as E
//                        }
//                    }
//
//                    if (flatCombiningLock.tryLock()) {
//                        combineOperations()
//                        flatCombiningLock.unlock()
//                    }
//                }
//            }
//        }
    }

    private fun combineOperations() {
        for (i in 0 until flatCombiningArray.size) {
            val operation = flatCombiningArray[i].value
            if (operation != null) {
                when (operation.status) {
                    OperationStatus.POLL -> flatCombiningArray[i].value = Operation(OperationStatus.DONE, q.poll())
                    OperationStatus.PEEK -> flatCombiningArray[i].value = Operation(OperationStatus.DONE, q.peek())
                    OperationStatus.ADD -> {
                        q.add(operation.element!!)
                        flatCombiningArray[i].value = Operation(OperationStatus.DONE)
                    }

                    OperationStatus.DONE -> flatCombiningArray[i].value = null
                }
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return flatCombining({ q.poll() }, Operation(OperationStatus.POLL))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return flatCombining({ q.peek() }, Operation(OperationStatus.PEEK))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        flatCombining({ q.add(element) }, Operation(OperationStatus.ADD, element))
    }
}