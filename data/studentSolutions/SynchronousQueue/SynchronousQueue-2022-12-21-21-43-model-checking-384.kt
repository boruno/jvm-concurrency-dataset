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
        while (true) {
            val cur_head = head.value
            val cur_tail = tail.value
            val cur_head_value = cur_head.x.value as Pair<Continuation<Any>, Optional<E>?>?
            if (cur_head != cur_tail && (cur_head_value == null || cur_head_value.second == null)) {
                if (cur_tail.next.value != null) {
                    if (tail.compareAndSet(cur_tail, cur_tail.next.value!!)) {
                        val cur_value = cur_tail.next.value!!.x.value as Pair<Continuation<Any>, Optional<E>?>
                        cur_tail.next.value!!.x.getAndSet(Pair(cur_value.first, Optional.ofNullable(element)))
                        cur_value.first.resume(true)
                        return
                    }
                }
            } else {
                val next = Node()
                val res = suspendCoroutine<Any> sc@ { cont ->
                    next.x.compareAndSet(null, Pair(cont, Optional.ofNullable(element)))
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
        while (true) {
            val cur_head = head.value
            val cur_tail = tail.value
            val cur_head_value = cur_head.x.value as Pair<Continuation<Any>, Optional<E>?>?
            if (cur_head != cur_tail && (cur_head_value == null || cur_head_value.second != null)) {
                if (cur_tail.next.value != null) { 
                    if (tail.compareAndSet(cur_tail, cur_tail.next.value!!)) {
                        val cur_value = cur_tail.next.value!!.x.value as Pair<Continuation<Any>, Optional<E>?>
                        cur_value.first.resume(true)
                        return cur_value.second!!.get()
                    }
                }
            } else {
                val next = Node()
                val res = suspendCoroutine<Any> sc@ { cont ->
                    next.x.compareAndSet(null, Pair(cont, null))
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