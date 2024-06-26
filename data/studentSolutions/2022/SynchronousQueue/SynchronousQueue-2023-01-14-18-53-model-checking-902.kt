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

    companion object {
        private val RETRY = Any()
    }

    private val head: AtomicRef<Node<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Node<E>> // Tail pointer, similarly to the Michael-Scott queue

//    From ffa
//    private val sendIdx = atomic(0L)
//    private val receiveIdx = atomic(0L)

    init {
        val firstNode = Node<E>(null, BlockType.NOTHING)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private suspend fun specialSuspendCoroutine(offer: Node<E>, t: Node<E>): Any {
        return suspendCoroutine sc@{ cont ->
            offer.nodeCont.value = cont
            if (t.casNext(null, offer)) {
                tail.compareAndSet(t, offer)
            } else {
                t.next.value?.let { tail.compareAndSet(t, it) }
                cont.resume(RETRY)
                return@sc
            }
        }
    }

    private fun skipCondition(t: Node<E>, h: Node<E>, n: Node<E>?) = t != tail.value || h != head.value || n == null


    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        val offer = Node(element, BlockType.SEND_TYPE)

        while (true) {
            val h = head.value
            val t = tail.value
            if (h == t || !t.isReceiver()) {
                val res = specialSuspendCoroutine(offer, t)
                if (res != RETRY) break
            } else {
                val n = h.next.value
                if (skipCondition(t, h, n)) continue

                h.next.value?.let { currNext ->
                    if (head.compareAndSet(h, currNext)) {
                        currNext.nodeCont.value?.resume(element!!)
                        return
                    }
                }
            }
        }
    }


    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun receive(): E {
        val offer = Node<E>(null, BlockType.RECEIVE_TYPE)

        while (true) {
            val h = head.value
            val t = tail.value
            if (h == t || !t.isSender()) {
                val res: Any = specialSuspendCoroutine(offer, t)
                if (specialSuspendCoroutine(offer, t) != RETRY) return res as E
            } else {
                val n = h.next.value
                if (skipCondition(t, h, n)) continue

                h.next.value?.let { currNext ->
                    if (head.compareAndSet(h, currNext)) {
                        val sender = currNext.nodeCont.value
                        val elem = currNext.data.value!!
                        currNext.nodeCont.value?.resume(Unit)
                        return elem
                    }
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
        val nodeCont: AtomicRef<Continuation<Any>?> = atomic(null)

        // like isRequest() from doc
        fun isReceiver() = blockType == BlockType.RECEIVE_TYPE
        fun isSender() = blockType == BlockType.SEND_TYPE

        fun casNext(expected: Node<E>?, updated: Node<E>?) = next.compareAndSet(expected, updated)
        fun casData(expected: E?, updated: E?) = data.compareAndSet(expected, updated)

    }

}