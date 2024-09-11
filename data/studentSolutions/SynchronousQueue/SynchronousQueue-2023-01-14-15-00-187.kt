import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.Continuation

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {


    private val head: AtomicRef<Node<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Node<E>> // Tail pointer, similarly to the Michael-Scott queue

    // From ffa
    private val sendIdx = atomic(0L)
    private val receiveIdx = atomic(0L)

    init {
        val firstNode = Node<E>(null, BlockType.NOTHING)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }


    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        val offer = Node(element, BlockType.SEND_TYPE)

        while (true) {
            val h = head.value
            val t = tail.value

            if (h == t || !t.isSender()) {

//                val res = suspendCoroutine<Any> sc@ { cont ->
//                    ...
//                    if (shouldRetry()) {
//                        cont.resume(RETRY)
//                        return@sc
//                    }
//                    ...
//                }
//                if (res === RETRY) continue



                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else if (t.casNext(n, offer)) {
                        val res = suspendCoroutine<Any?> sc@ { cont ->
                            offer.nodeCont = cont
                            if (!t.next.compareAndSet(null, offer)) {
                                tail.compareAndSet(t, t.next.value!!) // to value set
                                cont.resume(true)
                                return@sc
                            } else {
                                tail.compareAndSet(t, offer)  // to value set
                            }
                        }

                        if (res == true) continue

                        val updH = head.value
                        if (offer == updH.next.value) {
                            head.compareAndSet(updH, offer)
                        }
                        return
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) continue

                val success = n.casData(null, element)
                head.compareAndSet(h, n)
                if (success) {
                    n.nodeCont!!.resume(false)
                    return
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val offer = Node<E>(null, BlockType.RECEIVE_TYPE)

        while (true) {
            val h = head.value
            val t = tail.value

            if (h == t || !t.isReceiver()) {

//                val res = suspendCoroutine<Any?> sc@ { cont ->
//                    offer.nodeCont = cont
//                    if (!tail.value.next.compareAndSet(null, offer)) {
//                        tail.compareAndSet(tail.value, tail.value.next.value!!) // to value set
//                        cont.resume(true)
//                        return@sc
//                    } else {
//                        tail.compareAndSet(tail.value, offer)  // to value set
//                    }
//                }
//
//                if (res == true) continue
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else if (t.casNext(n, offer)) {
                        tail.compareAndSet(t, offer)
                        val res = suspendCoroutine<Any?> sc@ { cont ->
                            offer.nodeCont = cont
                            if (!t.next.compareAndSet(null, offer)) {
                                tail.compareAndSet(t, t.next.value!!) // to value set
                                cont.resume(true)
                                return@sc
                            } else {
                                tail.compareAndSet(t, offer)  // to value set
                            }
                        }

                        if (res == true) continue

                        val updH = head.value
                        if (offer == updH.next.value) {
                            head.compareAndSet(updH, offer)
                        }
                        return offer.data.value!!
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null) continue

                val currentNode = n.next.value ?: continue
                val receiveElement = currentNode.data.value
                val success = n.casData(receiveElement, null)
                head.compareAndSet(h, n)
                if (success) {
                    n.nodeCont!!.resume(false)
                    return receiveElement!!
                }
            }
        }
    }

    private enum class BlockType {
        SEND_TYPE, RECEIVE_TYPE, NOTHING
    }

    private class Node<E>(private val element: E?, private val blockType: BlockType) {
        val next = atomic<Node<E>?>(null)
        val data = atomic<E?>(element)
        var nodeCont: Continuation<Any>? = null

        // like isRequest() from doc
        fun isReceiver() = blockType == BlockType.RECEIVE_TYPE

        fun isSender() = blockType == BlockType.SEND_TYPE

        fun casNext(expected: Node<E>?, updated: Node<E>?) = next.compareAndSet(expected, updated)

        fun casData(expected: E?, updated: E?) = data.compareAndSet(expected, updated)

    }

}