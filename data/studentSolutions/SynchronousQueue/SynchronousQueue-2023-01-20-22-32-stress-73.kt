import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val SHOULD_RETRY = Any()

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(null, false, null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private suspend fun <T> schedule(mode: Boolean, exVal: Any?): T {
        while (true) {
            val h = head.value
            val next = h.next.value
            if (next == null || next.mode == mode) {
                val t = tail.value
                if (t.mode != mode) { continue }
                val result = suspendCoroutine<Any> { selfCoro ->
                    val n = Node(selfCoro, mode, exVal)
                    if (!t.next.compareAndSet(null, n)) {
                        val t1 = t.next.value
                        tail.compareAndSet(t, t1!!)
                        selfCoro.resume(SHOULD_RETRY)
                    } else {
                        tail.compareAndSet(t, n)
                    }
                }
                if (result == SHOULD_RETRY) { continue }
                return result as T
            } else {
                if (head.compareAndSet(h, next)) {
                    next.coroutine!!.resume(exVal!!)
                    return next.data as T
                }
            }
        }
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit = schedule(false, element)

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E = schedule(true, Unit)
}

private class Node(
    val coroutine: Continuation<Any>?,
    val mode: Boolean,
    val data: Any?
) {
    val next = atomic<Node?>(null)
}