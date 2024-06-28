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

    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    companion object {
        private val RETRY = Any()
    }

    init {
        val dummy = Node<E>()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {

        val offer = DataNode(element)

        while (true) {
            val t = tail.value
            var h = head.value
            if (h == t || t !is RequestNode) {
                val n = t.next.value
                if (t == tail.value) {
                    if (null != n) {
                        tail.compareAndSet(t, n)
                    } else {

                        val answer = suspendCoroutine<Any?> { cont ->
                            offer.cont = cont
                            if (t.next.compareAndSet(n, offer)) {
                                tail.compareAndSet(t, offer)
                            } else {
                                cont.resume(RETRY)
                            }
                        }
                        if (answer === RETRY) {
                            continue
                        }
                        h = head.value
                        if (offer == h.next.value)
                            head.compareAndSet(h, offer)
                        return
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null)
                    continue
                if (n is RequestNode) {
                    n.cont.resume(Unit)
                    head.compareAndSet(h, n)
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
        val offer = RequestNode<E>()


        while (true) {
            val t = tail.value
            var h = head.value
            if (h == t || t is RequestNode) {
                val n = t.next.value
                if (t == tail.value) {
                    if (null != n) {
                        tail.compareAndSet(t, n)
                    } else {

                        val answer = suspendCoroutine<Any?> { cont ->
                            offer.cont = cont
                            if (t.next.compareAndSet(n, offer)) {
                                tail.compareAndSet(t, offer)
                            } else {
                                cont.resume(RETRY)
                            }
                        }
                        if (answer === RETRY) {
                            continue
                        }
                        h = head.value
                        if (offer == h.next.value)
                            head.compareAndSet(h, offer)
                        return answer as E
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || h != head.value || n == null)
                    continue
                if (n is DataNode) {
                    n.cont.resume(Unit)
                    head.compareAndSet(h, n)
                    return n.data
                }
            }
        }
    }
}

private open class Node<E>() {
    val next : AtomicRef<Node<E>?> = atomic(null)
    lateinit var cont: Continuation<Any?>
}

private class DataNode<E>(val data: E) : Node<E>() {

}

private class RequestNode<E>() : Node<E>() {

}