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
class SynchronousQueue<E : Any> {
    private val head: AtomicRef<CoroutineNode<E>>
    private val tail: AtomicRef<CoroutineNode<E>>

    init {
        val dummy = CoroutineNode<E>(null, null, "")
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val curTail = tail.value
            val curHead = head.value
            if (curTail == curHead || curTail.type == "SENDER") {
                val RETRY = ""
                var needRetry = false
                val res = suspendCoroutine sc@ { cont ->
                    val node = CoroutineNode(cont, element, "SENDER")
                    if (tail.value.next.compareAndSet(null, node)) {
                        if (!tail.compareAndSet(curTail, node)) {
                            needRetry = true
                        }
                    } else {
                        needRetry = true
                    }

                    if (needRetry) {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (res === RETRY) continue
                break
            } else {
                val curHeadNext = curHead.next.value!!
                if (head.compareAndSet(curHead, curHeadNext)) {
                    curHeadNext.cont!!.resume(element)
                    break
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val curTail = tail.value
            val curHead = head.value
            if (curTail == curHead || curTail.type == "RECEIVER") {
                val RETRY = ""
                var needRetry = false
                val res = suspendCoroutine sc@ { cont ->
                    val node = CoroutineNode<E>(cont, null, "RECEIVER")
                    if (tail.value.next.compareAndSet(null, node)) {
                        if (!tail.compareAndSet(curTail, node)) {
                            needRetry = true
                        }
                    } else {
                        needRetry = true
                    }

                    if (needRetry) {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (res === RETRY) continue
                return res as E
            } else {
                val curHeadNext = curHead.next.value!!
                if (head.compareAndSet(curHead, curHeadNext)) {
                    curHeadNext.cont!!.resume(Unit)
                    return curHeadNext.x!!
                }
            }
        }
    }

    private class CoroutineNode<E>(val cont: Continuation<Any>?, val x: E?, val type: String) {
        val next = atomic<CoroutineNode<E>?>(null)
    }

    class MSQueue<E> {
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
                if (curTail.next.compareAndSet(null, node)) {
                    tail.compareAndSet(curTail, node)
                    return
                } else {
                    curTail.next.value?.let { tail.compareAndSet(curTail, it) }
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
                val curHeadNext = curHead.next.value ?: return null
                if (head.compareAndSet(curHead, curHeadNext)) {
                    return curHeadNext.x
                }
            }
        }

        fun isEmpty(): Boolean {
            return head.value.next.value == null
        }
    }


    private class Node<E>(val x: E?) {
        val next = atomic<Node<E>?>(null)
    }
}