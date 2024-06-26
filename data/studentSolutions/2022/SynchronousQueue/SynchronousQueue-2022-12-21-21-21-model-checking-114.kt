import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.util.*
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
        while (true) {
            if (s < receiveIdx.value) {
                val cur = tail.value
                if (cur.next.value != null && tail.compareAndSet(cur, cur.next.value!!)) {
                    val cur_value = cur.next.value!!.x.value as Pair<Continuation<Any>, Optional<E>?>
                    cur.next.value!!.x.getAndSet(Pair(cur_value.first, Optional.ofNullable(element)))
                    cur_value.first.resume(true)
                    return
                }
            } else {
                val next = Node()
                val res = suspendCoroutine<Any> sc@ { cont ->
                    next.x.compareAndSet(null, Pair(cont, Optional.ofNullable(element)))
                    val cur_head = head.value
                    if (cur_head.next.compareAndSet(null, next)) {
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
        val r = receiveIdx.getAndAdd(1)
        while (true) {
            if (r < sendIdx.value) {
                val cur = tail.value
                if (cur.next.value != null && tail.compareAndSet(cur, cur.next.value!!)) {
                    val cur_value = cur.next.value!!.x.value as Pair<Continuation<Any>, Optional<E>?>
                    cur_value.first.resume(true)
                    return cur_value.second!!.get()
                }
            } else {
                val next = Node()
                val res = suspendCoroutine<Any> sc@ { cont ->
                    next.x.compareAndSet(null, Pair(cont, null))
                    val cur_head = head.value
                    if (cur_head.next.compareAndSet(null, next)) {
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
                    return (next.x.value as Pair<Continuation<Any>, Optional<E>>).second.get()
                }
            }
        }
    }
}

class Node {
    val next: AtomicRef<Node?> = atomic(null)
    val x: AtomicRef<Any?> = atomic(null)
}