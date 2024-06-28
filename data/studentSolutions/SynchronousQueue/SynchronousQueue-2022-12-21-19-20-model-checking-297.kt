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
        var s = sendIdx.getAndAdd(1)
        while (true) {
            if (s < receiveIdx.value) {
                val cur = head.value
                val cur_value = cur.value.value as Pair<Continuation<Any>, E>?
                if (cur_value != null && cur_value.second == null) {
                    if (cur.value.compareAndSet(cur_value, Pair(cur_value.first, element))) {
                        cur_value.first.resume(true)
                        return
                    }
                } else {
                    var b = false
                    if (cur.next.value == null) {
                        b = cur.next.compareAndSet(null, Node())
                    }
                    b = head.compareAndSet(cur, cur.next.value!!) && b
                    if (b) {
                        s = sendIdx.getAndAdd(1)
                    }
                }
            } else {
                val next = Node()
                val res = suspendCoroutine<Any> sc@ { cont ->
                    next.value.compareAndSet(null, Pair(cont, element))
                    val cur_head = head.value
                    if (head.value.next.compareAndSet(null, next)) {
                        head.compareAndSet(cur_head, next)
                    } else {
                        if (cur_head.next.value != null) {
                            head.compareAndSet(cur_head, cur_head.next.value!!)
                        }
                        cont.resume(false)
                        return@sc
                    }
                }
                if (res == false) {
                    continue
                } else {
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
        var r = receiveIdx.getAndAdd(1)
        while (true) {
            if (r < sendIdx.value) {
                val cur = tail.value
                val cur_value = cur.value.value as Pair<Continuation<Any>, E>?
                if (cur_value?.second != null) {
                    if (cur.value.compareAndSet(cur_value, Pair(cur_value.first, null))) {
                        cur_value.first.resume(true)
                        return cur_value.second
                    }
                } else {
                    var b = false
                    if (cur.next.value == null) {
                        b = cur.next.compareAndSet(null, Node())
                    }
                    b = tail.compareAndSet(cur, cur.next.value!!) && b
                    if (b) {
                        r = receiveIdx.getAndAdd(1)
                    }
                }
            } else {
                val next = Node()
                val res = suspendCoroutine<Any> sc@ { cont ->
                    next.value.compareAndSet(null, Pair(cont, null))
                    val cur_tail = tail.value
                    if (tail.value.next.compareAndSet(null, next)) {
                        tail.compareAndSet(cur_tail, next)
                    } else {
                        if (cur_tail.next.value != null) {
                            tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                        }
                        cont.resume(false)
                        return@sc
                    }
                }
                if (res == false) {
                    continue
                } else {
                    return (next.value.value as Pair<Continuation<Any>, E>).second
                }
            }
        }
    }
}

class Node {
    val next: AtomicRef<Node?> = atomic(null)
    val value: AtomicRef<Any?> = atomic(null)
}