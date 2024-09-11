import FCPriorityQueue.Opcode.*
import kotlinx.atomicfu.atomic
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val qLock = FcLock()
    private val publicationList = ConcurrentLinkedQueue<Operation>()

    //    private val fcArray = arrayOfNulls<Operation>(Runtime.getRuntime().availableProcessors() * 2) Maybe it's better than ConcurrentLinkedQueue

    private fun tryScanCombineApply() {
        if (qLock.tryLock()) {
            var i = 0
            while (true) {
                val op = publicationList.poll()
                if (i > 100) {
                    break
                }
                when (op.opcode) {
                    POLL -> op.result = Result(true, q.poll())
                    PEEK -> op.result = Result(true, q.peek())
                    ADD -> op.result = Result(true, q.add(op.args.first() as E))
                }
                i++
            }

            qLock.unlock()
        }
    }

    private fun doOperation(op: Operation): E? {
        publicationList.add(op)
        var i = 0
        tryScanCombineApply()
        while (true) {
            if (i >= WAITING) {
                tryScanCombineApply()
                i = 0
            }
            if (op.result.done) {
                return op.result.value as E?
            }
            i++
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return doOperation(Operation(POLL))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return doOperation(Operation(PEEK))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        doOperation(Operation(ADD, listOf(element)))
    }

    private class FcLock {
        val locked = atomic(false)

        fun tryLock() = locked.compareAndSet(expect = false, update = true)

        fun unlock() {
            locked.value = false
        }
    }

    private enum class Opcode {
        POLL,
        PEEK,
        ADD
    }

    private data class Result(val done: Boolean = false, val value: Any? = null)

    private data class Operation(val opcode: Opcode, val args: List<Any> = listOf(), var result: Result = Result())

    companion object {
        private const val WAITING = 20
    }
}