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
    val head: AtomicRef<Node>
    val tail: AtomicRef<Node>
    val sendIdx = atomic(0L)
    val receiveIdx = atomic(0L)

    init {
        val node = Node()
        head = atomic(node)
        tail = atomic(node)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val s = sendIdx.getAndAdd(1)
        if (s < receiveIdx.value) {
            while (true) {
                val cur = head.value
                val cur_value = cur.value.value as Pair<Any?, Continuation<E>>?
                if (cur_value != null) {
                    if (cur.value.compareAndSet(cur_value, null)) {
                        cur_value.second.resume(element)
                        return
                    }
                } else if (cur.next.value != null) {
                    head.compareAndSet(cur, cur.next.value!!)
                }
            }
        } else {
            suspendCoroutine { unit ->
                val next = Node()
                next.value.compareAndSet(null, Pair(Pair(unit, element), null))
                while (true) {
                    val cur_head = head.value
                    if (head.value.next.compareAndSet(null, next)) {
                        head.compareAndSet(cur_head, next)
                        break
                    } else if (cur_head.next.value != null) {
                        head.compareAndSet(cur_head, cur_head.next.value!!)
                    }
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val r = receiveIdx.getAndAdd(1)
        if (r < sendIdx.value) {
            while (true) {
                val cur = tail.value
                val cur_value = cur.value.value as Pair<Pair<Continuation<Unit>, E>, Any?>?
                if (cur_value != null) {
                    if (cur.value.compareAndSet(cur_value, null)) {
                        cur_value.first.first.resume(Unit)
                        return cur_value.first.second
                    }
                } else if (cur.next.value != null) {
                    tail.compareAndSet(cur, cur.next.value!!)
                }
            }
        } else {
            return suspendCoroutine { unit ->
                val next = Node()
                next.value.compareAndSet(null, Pair(null, unit))
                while (true) {
                    val cur_tail = tail.value
                    if (tail.value.next.compareAndSet(null, next)) {
                        tail.compareAndSet(cur_tail, next)
                        break
                    } else if (cur_tail.next.value != null) {
                        tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                    }
                }
            }
        }
    }
}

class Node {
    val next: AtomicRef<Node?> = atomic(null)
    val value: AtomicRef<Any?> = atomic(null)
}