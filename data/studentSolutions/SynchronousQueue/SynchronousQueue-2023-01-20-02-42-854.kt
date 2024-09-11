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
            if ( h == t || t.send != null ) {
                var cur_next = t.next.value
                if ( t == tail.value ) {
                    if (cur_next != null) {
                        tail.compareAndSet(t, cur_next)
                    } else {
                        if (suspendCoroutine { cont ->
                                Node<E>(element, cont, null)
                                node = Node<E>(element,cont,null)
                                if (t.next.compareAndSet(null, node)) {
                                    tail.compareAndSet(t, node)
                                } else {
                                    cont.resume(continuee())
                                }
                            } == continuee()) {
                            continue
                        }
                        h = head.value
                        if (node == h.next.value) {
                            head.compareAndSet(h, node)
                        }
//                    return node.value!!
                    }
                }
            } else {
                if ( h != head.value || t != tail.value || t == h ) {
                    continue
                } else {
                    var vv = h.next.value
                    var noda = Node<E>(element, null, vv!!.receivers!! )
                    if ( h.next.compareAndSet( vv, node ) ) {
                        head.compareAndSet( h, vv!! )
                        vv.receivers!!.resume(Unit)
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
            if ( h == t || t.send != null ) {
                var cur_next = t.next.value
                if (t == tail.value) {
                    if (cur_next != null) {
                        tail.compareAndSet(t, cur_next)
                    } else {
                        if (suspendCoroutine { cont ->
                                Node<E>(null, null, cont)
                                node = Node<E>(null,null,cont)
                                if (t.next.compareAndSet(null, node)) {
                                    tail.compareAndSet(t, node)
                                } else {
                                    cont.resume(continuee())
                                }
                            } == continuee()) {
                            continue
                        }
                        h = head.value
                        if (node == h.next.value) {
                            head.compareAndSet(h, node)
                        }
                        return node.value!!
                    }
                }
            } else {
                if ( h != head.value || t != tail.value || t == h ) {
                    continue
                } else {
                    var vv = h.next.value
                    var noda = Node<E>(null, vv!!.senders!!, null)
                    if ( h.next.compareAndSet( vv, node ) ) {
                        head.compareAndSet( h, vv!! )
                        vv.senders!!.resume(Unit)
                        if ( h.value != null) {
                            return h.value!!
                        } else {
                            return 0 as E
                        }
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

class Node<E>(var x: E?, var send: Continuation<Unit>?, rec : Continuation<Unit>? ) {
    val next = atomic<Node<E>?>(null)
    var value = x
    var senders = send
    var receivers = rec

}

// 0 sender
// 1 receiver