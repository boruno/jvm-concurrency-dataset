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
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */

    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, null, null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }


        suspend fun send(element: E): Unit {
            var node : Node<E>? = null
            if ( tail.value == head.value || tail.value.send != null ) {
                var t = tail.value
                var h = head.value
                node = Node<E>(element, null, null)
                var next_tail = node
                var cur_tail = tail.value
                var cor = suspendCoroutine<Unit> { cont ->
                    Node<E>(element, cont, null)
                    next_tail.send = cont
                        if (cur_tail.next.compareAndSet(null, next_tail)) {
                            if (tail.compareAndSet(cur_tail, next_tail)) {
                            } else {
                                cont.resume(Unit)
                            }
                        } else {
                            tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                        }
                }
            } else {
                var a = dequeue()
                var s = a!!.receivers!!
                s.resume(element as Int)
            }
//            return node!!.value!!
        }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        var t = tail
        var node : Node<E>? = null
        var h = head
        if (t.value == h.value || t.value.receivers != null) {
                var cor = suspendCoroutine<Int> { cont ->
                    Node<E>(null, null, cont)
                    var t = tail
                    var h = head
                    if (t.value == h.value || t.value.send != null) {
                        node = Node<E>(null, null, cont)
                        while (true) {
                            val next_tail = node!!
                            val cur_tail = tail.value
                            if (cur_tail.next.compareAndSet(null, next_tail)) {
                                if (tail.compareAndSet(cur_tail, next_tail)) {
                                    break
                                } else {
                                    cont.resume(Unit as Int)
                                }
                            } else {
                                tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                            }
                        }
                    } else {
                        cont.resume(Unit as Int)
                    }
                }
            } else {
                var a = dequeue()
                var s = a!!.senders!!
                s.resume(Unit)
            }
        return node!!.value!!
    }

    fun dequeue(): Node<E>? {
        while ( true ) {
            val cur_head = head.value
            if ( isEmpty() ) {
                return null
            }
            var cur_head_next = cur_head.next.value
            if ( cur_head_next == null ) {
                return null
            } else {
                if ( head.compareAndSet( cur_head, cur_head_next ) ) {
                    return cur_head_next
                }
            }
        }
    }

        fun isEmpty(): Boolean {
            return head.value == tail.value
        }



}


class Node<E>(val x: E?, var send: Continuation<Unit>?, rec : Continuation<Int>? ) {
    val next = atomic<Node<E>?>(null)
    var value = x
    var senders = send
    var receivers = rec

}

// 0 sender
// 1 receiver