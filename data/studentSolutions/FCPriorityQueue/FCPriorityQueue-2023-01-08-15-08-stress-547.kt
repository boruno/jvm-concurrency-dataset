import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

sealed interface Operation {
    object Poll : Operation
    object Peek : Operation
    data class Add<E>(val element: E) : Operation
}

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val isQueueLocked = atomic(false)
    private val fcArray = atomicArrayOfNulls<Operation>(ARRAY_SIZE)

//    fun becomeCombiner(operation: Operation): E? {
//        for (i in 0 until ARRAY_SIZE) {
//            val elementOperation = fcArray[i].value
//            if (elementOperation != null) {
//                fcArray[i].compareAndSet(elementOperation, null)
////                when (elementOperation) {
////                    Operation.Poll, Operation.Peek -> {
////                        fcArray[i].compareAndSet(elementOperation, null)
////                    }
////                    is Operation.Add<*> -> {
////                        fcArray[i].compareAndSet(elementOperation, null)
//////                        q.add(elementOperation.element as E)
////                    }
////                }
//            }
//        }
//
//        isQueueLocked.compareAndSet(true, false)
//
////        when (operation) {
////            Operation.Poll -> return q.poll()
////            Operation.Peek -> return q.peek()
////            is Operation.Add<*> -> {
////                q.add(operation.element as E)
////                return null
////            }
////        }
//
////        isQueueLocked.compareAndSet(true, false)
//    }

    fun becomeCombinerOrWait(operation: Operation) : E? {
        var i: Int
        while (true) {
            i = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
            if (fcArray[i].compareAndSet(null, operation)) { break }
        }

        while (true) {
            if (fcArray[i].value == null) { break }

            if (isQueueLocked.compareAndSet(false, true)) {
                for (i in 0 until ARRAY_SIZE) {
                    val elementOperation = fcArray[i].value
                    if (elementOperation != null) {
                        fcArray[i].compareAndSet(elementOperation, null)
                        if (elementOperation is Operation.Add<*>) { q.add(elementOperation.element as E) }
                    }
                }
                isQueueLocked.compareAndSet(true, false)
            }
        }

        when (operation) {
            Operation.Poll -> return q.poll()
            Operation.Peek -> return q.peek()
            is Operation.Add<*> -> { return null }
        }
    }
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return becomeCombinerOrWait(Operation.Poll)
//        var i: Int
//        while (true) {
//            i = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
//            if (fcArray[i].compareAndSet(null, Operation.Poll)) { break }
//        }
//
//        while (true) {
//            if (fcArray[i].value == null) { break/*return q.poll()*/ }
//
//            if (isQueueLocked.compareAndSet(false, true)) {
//                for (i in 0 until ARRAY_SIZE) {
//                    val elementOperation = fcArray[i].value
//                    if (elementOperation != null) {
//                        fcArray[i].compareAndSet(elementOperation, null)
//                    }
//                }
//            }
//            isQueueLocked.compareAndSet(true, false)
//        }
//
//        return q.poll()
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return becomeCombinerOrWait(Operation.Peek)
//        var i: Int
//        while (true) {
//            i = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
//            if (fcArray[i].compareAndSet(null, Operation.Poll)) { break }
//        }
//
//        while (true) {
//            if (fcArray[i].value == null) { break /*return q.peek()*/ }
//
//            if (isQueueLocked.compareAndSet(false, true)) {
//                return becomeCombiner(Operation.Peek)
//            }
//        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        becomeCombinerOrWait(Operation.Add(element))
//            var i: Int
//            while (true) {
//                i = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
//                if (fcArray[i].compareAndSet(null, Operation.Poll)) { break }
//            }
//
//            while (true) {
//                if (fcArray[i].value == null) { break }
//
//                if (isQueueLocked.compareAndSet(false, true)) {
//                    becomeCombiner(Operation.Add(element))
//                }
//            }
    }
}

private const val ARRAY_SIZE = 30