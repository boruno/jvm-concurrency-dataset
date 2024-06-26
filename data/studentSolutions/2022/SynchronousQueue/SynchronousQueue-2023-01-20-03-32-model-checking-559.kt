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
        var node = Node<E> (element, null, null )
        while ( true ) {
            var h = head.value
            var t = tail.value
            if ( h == t || t.senders != null ) {
                var cur_next = t.next.value
                if ( t == tail.value ) {
                    if (cur_next != null) {
                        tail.compareAndSet(t, cur_next)
                    } else {
                        var sc = suspendCoroutine<Unit?>{ cont ->
                                Node<E>(element, cont, null)
                                node = Node<E>(element,cont,null)
                                if (t.next.compareAndSet(null, node)) {
                                    tail.compareAndSet(t, node)
                                }
                            }
                        if ( sc != null ) {
                            return
                        }
                    }
                }
            } else {
                if ( h != head.value || t != tail.value || t == h ) {
                    continue
                } else {
                    var next = h.next.value!!
                    if ( head.compareAndSet( h, next ) ) {
                        next.receivers!!.resume( element )
                        return
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
        var node = Node<E> (null, null, null )
        while ( true ) {
            if ( node.receivers != null ) {
                if (node.x != null)
                    return node.x!!
                else
                    return -1234 as E
            }
            var h = head.value
            var t = tail.value
            if ( h == t || t.receivers != null ) {
                var cur_next = t.next.value
                if (t == tail.value) {
                    if (cur_next != null) {
                        tail.compareAndSet(t, cur_next)
                    } else {
                        var sc = suspendCoroutine<E?> { cont ->
                                Node<E>(null, null, cont)
                                node = Node<E>(null,null,cont)
                                if (t.next.compareAndSet(null, node)) {
                                    tail.compareAndSet(t, node)
//                                    return null as E?
                                } else {
                                    return@suspendCoroutine
                                }
                            }
                        if ( sc != null ) {
                            return sc
                        }
                    }
                }
            } else {
                if ( h != head.value || t != tail.value || t == h ) {
                    continue
                } else {
                    var vv = h.next.value
                    if ( vv!!.senders == null) continue
                    if ( head.compareAndSet( h, vv!! ) ) {
                        vv.senders!!.resume(Unit)
                        return vv.value!!
                    }
                }
            }
        }
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

fun continuee(){}

class Node<E>(var x: E?, var send: Continuation<Unit>?, rec : Continuation<E?>? ) {
    val next = atomic<Node<E>?>(null)
    var value = x
    var senders = send
    var receivers = rec

}

// 0 sender
// 1 receiver