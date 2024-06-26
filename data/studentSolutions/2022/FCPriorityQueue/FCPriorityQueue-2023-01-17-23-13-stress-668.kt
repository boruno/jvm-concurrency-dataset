import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock

//class Instruction<E>(val data : E) {
//}

enum class OpType {
    RESULT, POLL, PEEK, ADD
}

class Instruction<E>(var opType: OpType, var data : E?) {
}

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val combinerLock = ReentrantLock()
    private val instructionQueue = atomicArrayOfNulls<Instruction<E>>(3 * Runtime.getRuntime().availableProcessors())

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */

    fun tryCombine(instruction: Instruction<E>): Instruction<E>? {
        while (true) {
            if (!combinerLock.tryLock()) {
                var index = ThreadLocalRandom.current().nextInt(instructionQueue.size)
                while (!instructionQueue[index].compareAndSet(null, instruction)) {
                    index = ThreadLocalRandom.current().nextInt(instructionQueue.size)
                }

                var res = instructionQueue[index].value
                while (res != null && res.opType != OpType.RESULT) {
                    res = instructionQueue[index].value
                }
                return res
            }
            for (i in 0 until instructionQueue.size) {
                val operation = instructionQueue[i].value
                if (operation != null) {
                    when (operation.opType) {
                        OpType.RESULT -> {}
                        OpType.POLL -> operation.data = q.poll()
                        OpType.PEEK -> operation.data = q.peek()
                        OpType.ADD -> q.add(operation.data)
                    }
                    operation.opType = OpType.RESULT
                }
            }
        }
    }
    fun poll(): E? {
        return tryCombine(Instruction(OpType.POLL, null))?.data
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return tryCombine(Instruction(OpType.PEEK, null))?.data
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        tryCombine(Instruction(OpType.ADD, element))
    }
}