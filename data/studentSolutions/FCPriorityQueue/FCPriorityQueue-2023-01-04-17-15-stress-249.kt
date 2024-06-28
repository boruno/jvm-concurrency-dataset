import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import kotlin.random.Random

//import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock: AtomicBoolean = atomic(false)
    private val fcArray: AtomicArray<Operation<E>?> = atomicArrayOfNulls(100)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val op = Operation<E>(opType.POLL)

        // TODO: несколько итераций
        var arrayNum = Random.nextInt(fcArray.size)
        var setHelp = fcArray[arrayNum].compareAndSet(null, op)

        while (true) {
            if (lock.compareAndSet(false, true)) {
                if (setHelp) {
                    val ourOp = fcArray[arrayNum].value

                    if (ourOp!!.status == opStatus.DONE) {
                        fcArray[arrayNum].compareAndSet(ourOp, null)
                        helpOtherThreads()
                        lock.compareAndSet(true, false)
                        return ourOp.result
                    }

                    fcArray[arrayNum].compareAndSet(op, null)
                }
                val res = q.poll()

                helpOtherThreads()
                lock.compareAndSet(true, false)
                return res
            } else if (setHelp) {
                val ourOp = fcArray[arrayNum].value

                if (ourOp!!.status == opStatus.DONE) {
                    fcArray[arrayNum].compareAndSet(ourOp, null)
                    return ourOp.result
                }
            } else {
                arrayNum = Random.nextInt(fcArray.size)
                setHelp = fcArray[arrayNum].compareAndSet(null, op)
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val op = Operation<E>(opType.PEEK)

        // TODO: несколько итераций
        var arrayNum = Random.nextInt(fcArray.size)
        var setHelp = fcArray[arrayNum].compareAndSet(null, op)

        while (true) {
            if (lock.compareAndSet(false, true)) {
                if (setHelp) {
                    val ourOp = fcArray[arrayNum].value

                    if (ourOp!!.status == opStatus.DONE) {
                        fcArray[arrayNum].compareAndSet(ourOp, null)
                        helpOtherThreads()
                        lock.compareAndSet(true, false)
                        return ourOp.result
                    }

                    fcArray[arrayNum].compareAndSet(op, null)
                }
                val res = q.peek()

                helpOtherThreads()
                lock.compareAndSet(true, false)
                return res
            } else if (setHelp) {
                val ourOp = fcArray[arrayNum].value

                if (ourOp!!.status == opStatus.DONE) {
                    fcArray[arrayNum].compareAndSet(ourOp, null)
                    return ourOp.result
                }
            } else {
                arrayNum = Random.nextInt(fcArray.size)
                setHelp = fcArray[arrayNum].compareAndSet(null, op)
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val op = Operation<E>(opType.ADD)
        op.result = element

        // TODO: несколько итераций
        var arrayNum = Random.nextInt(fcArray.size)
        var setHelp = fcArray[arrayNum].compareAndSet(null, op)

        while (true) {
            if (lock.compareAndSet(false, true)) {
                if (setHelp) {
                    val ourOp = fcArray[arrayNum].value

                    if (ourOp!!.status == opStatus.DONE) {
                        fcArray[arrayNum].compareAndSet(ourOp, null)
                        helpOtherThreads()
                        lock.compareAndSet(true, false)
                        return
                    }

                    fcArray[arrayNum].compareAndSet(op, null)
                }

                q.add(element)

                helpOtherThreads()
                lock.compareAndSet(true, false)
                return
            } else if (setHelp) {
                val ourOp = fcArray[arrayNum].value

                if (ourOp!!.status == opStatus.DONE) {
                    fcArray[arrayNum].compareAndSet(ourOp, null)
                    return
                }
            } else {
                arrayNum = Random.nextInt(fcArray.size)
                setHelp = fcArray[arrayNum].compareAndSet(null, op)
            }
        }
    }

    private fun helpOtherThreads() {
        for (ind in 0..(fcArray.size - 1)) {
            val op = fcArray[ind].value

            if (op != null) {
                val answ = Operation<E>(op.op)

                if (op.op == opType.ADD) {
                    q.add(op.result!!)
                    answ.status = opStatus.DONE
                    fcArray[ind].compareAndSet(op, answ)
                } else if (op.op == opType.PEEK) {
                    val res = q.peek()
                    answ.status = opStatus.DONE
                    answ.result = res
                    fcArray[ind].compareAndSet(op, answ)
                } else if (op.op == opType.POLL) {
                    val res = q.poll()
                    answ.status = opStatus.DONE
                    answ.result = res
                    fcArray[ind].compareAndSet(op, answ)
                }
            }
        }
    }
        enum class opType {
            POLL,
            PEEK,
            ADD,
        }
        enum class opStatus {
            DONE,
            EMPTY
        }

    private class Operation<E>(val op: opType) {
        var result: E? = null
        var status = opStatus.EMPTY
    }
}