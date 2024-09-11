import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val fcArray = atomicArrayOfNulls<FcArrayElement>(FC_ARRAY_SIZE)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
            if (lock.tryLock()) {
               return pollAsCombiner()
            } else {
                var takenIndex: Int? = null
                for (i in 0 until FC_ARRAY_SIZE) {
                    if (fcArray[i].value == null) {
                        if (!fcArray[i].compareAndSet(null, FcArrayElement.FcOperation.Poll)) {
                            continue
                        } else {
                            takenIndex = i
                            break
                        }
                    }
                }

                if (takenIndex == null) {
                    continue
                }

                while (fcArray[takenIndex].value !is FcArrayElement.FcAnswer && !lock.tryLock()) {
                    // wait
                }

                return if (fcArray[takenIndex].value is FcArrayElement.FcAnswer) {
                    if (lock.isHeldByCurrentThread) {
                        lock.unlock()
                    }

                    val answer = fcArray[takenIndex].value
                    fcArray[takenIndex].compareAndSet(answer, null)
                    (answer as FcArrayElement.FcAnswer.Poll<*>).element as? E
                } else {
                    pollAsCombiner()
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
            if (lock.tryLock()) {
                return peekAsCombiner()
            } else {
                var takenIndex: Int? = null
                for (i in 0 until FC_ARRAY_SIZE) {
                    if (fcArray[i].value == null) {
                        if (!fcArray[i].compareAndSet(null, FcArrayElement.FcOperation.Peek)) {
                            continue
                        } else {
                            takenIndex = i
                            break
                        }
                    }
                }

                if (takenIndex == null) {
                    continue
                }

                while (fcArray[takenIndex].value !is FcArrayElement.FcAnswer && !lock.tryLock()) {
                    // wait
                }

                return if (fcArray[takenIndex].value is FcArrayElement.FcAnswer) {
                    if (lock.isHeldByCurrentThread) {
                        lock.unlock()
                    }

                    val answer = fcArray[takenIndex].value
                    fcArray[takenIndex].compareAndSet(answer, null)
                    (answer as FcArrayElement.FcAnswer.Peek<*>).element as? E
                } else {
                    peekAsCombiner()
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
            if (lock.tryLock()) {
                return addAsCombiner(element)
            } else {
                var takenIndex: Int? = null
                for (i in 0 until FC_ARRAY_SIZE) {
                    if (fcArray[i].value == null) {
                        if (!fcArray[i].compareAndSet(null, FcArrayElement.FcOperation.Add(element))) {
                            continue
                        } else {
                            takenIndex = i
                            break
                        }
                    }
                }

                if (takenIndex == null) {
                    continue
                }

                while (fcArray[takenIndex].value !is FcArrayElement.FcAnswer && !lock.tryLock()) {
                    // wait
                }

                if (fcArray[takenIndex].value is FcArrayElement.FcAnswer) {
                    if (lock.isHeldByCurrentThread) {
                        lock.unlock()
                    }

                    val answer = fcArray[takenIndex].value
                    fcArray[takenIndex].compareAndSet(answer, null)
                    return
                } else {
                    return addAsCombiner(element)
                }
            }
        }
    }

    private fun pollAsCombiner(): E? {
        try {
            val element = q.poll()
            helpOthers()
            return element
        } finally {
            lock.unlock()
        }
    }

    private fun peekAsCombiner(): E? {
        try {
            val element = q.peek()
            helpOthers()
            return element
        } finally {
            lock.unlock()
        }
    }

    private fun addAsCombiner(element: E) {
        try {
            q.add(element)
            helpOthers()
        } finally {
            lock.unlock()
        }
    }

    private fun helpOthers() {
        for (i in 0 until FC_ARRAY_SIZE) {
            when (val op = fcArray[i].value) {
                is FcArrayElement.FcOperation.Add<*> -> {
                    q.add(op.element as? E)
                    fcArray[i].compareAndSet(null, FcArrayElement.FcAnswer.Add)
                }
                is FcArrayElement.FcOperation.Poll -> {
                    fcArray[i].compareAndSet(null, FcArrayElement.FcAnswer.Poll(q.poll()))
                }
                is FcArrayElement.FcOperation.Peek -> {
                    fcArray[i].compareAndSet(null, FcArrayElement.FcAnswer.Peek(q.peek()))
                }
                else -> Unit
            }
        }
    }
}

sealed class FcArrayElement {

    sealed class FcOperation : FcArrayElement() {

        data class Add<E>(val element: E): FcOperation()
        object Poll : FcOperation()
        object Peek : FcOperation()
    }

    sealed class FcAnswer : FcArrayElement() {

        data class Poll<E>(val element: E): FcAnswer()
        data class Peek<E>(val element: E): FcAnswer()
        object Add : FcAnswer()
    }
}

private const val FC_ARRAY_SIZE = 5