import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val sendersQueue: MSQueue<Pair<Continuation<Unit>, E>>
    private val readersQueue: MSQueue<Continuation<E>>

    init {
        sendersQueue = MSQueue()
        readersQueue = MSQueue()
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        if (sendersQueue.isEmpty()) {
            val reader = readersQueue.dequeue()
            if (reader != null) {
                reader.resume(element)

                return
            }
        }

        return suspendCoroutine { cont ->
            sendersQueue.enqueue(Pair(cont, element))
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val pair = sendersQueue.dequeue()
        if (pair != null) {
            pair.first.resume(Unit)
            return pair.second
        }

        return suspendCoroutine { cont ->
            readersQueue.enqueue(cont)
        }
    }

   private class MSQueue<E> {
        private val head: AtomicRef<Node<E>>
        private val tail: AtomicRef<Node<E>>

        init {
            val dummy = Node<E>(null)
            head = atomic(dummy)
            tail = atomic(dummy)
        }

        /**
         * Adds the specified element [x] to the queue.
         */
        fun enqueue(x: E) {
            while (true) {
                val node = Node(x)
                val curTail = tail.value
                val nextTail = curTail.next.value

                if (nextTail != null) {
                    tail.compareAndSet(curTail, nextTail)
                    continue
                }

                if (curTail.next.compareAndSet(null, node)) {
                    tail.compareAndSet(curTail, node)

                    return
                }
            }
        }

        /**
         * Retrieves the first element from the queue
         * and returns it; returns `null` if the queue
         * is empty.
         */
        fun dequeue(): E? {
            while (true) {
                val curHead = head.value
                val curHeadNext = curHead.next.value
                val curTail = tail.value

                if (curHead == tail.value) {
                    if (curHeadNext == null) {
                        return null
                    }
                    tail.compareAndSet(curTail, curHeadNext)
                    continue
                }

                if (curHeadNext != null && head.compareAndSet(curHead, curHeadNext)) {
                    return curHeadNext.x
                }
            }
        }

        fun isEmpty(): Boolean {
            return head.value == tail.value
        }

       private class Node<E>(val x: E?) {
           val next = atomic<Node<E>?>(null)
       }
    }
}