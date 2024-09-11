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
        while ( true ) {
            var node = Node<E> (element, null, null )
            var h = head.value
            var t = tail.value
            if ( h == t || t.send != null ) {
                var cur_next = t.next.value
                if ( t == tail.value ) {
                    if ( cur_next != null ) {
                        tail.compareAndSet( t, cur_next )
                    }
                } else {
                    if ( suspendCoroutine { cont ->
                            Node<E>( element, cont, null )
                            if ( t.next.compareAndSet( null, node ) ) {
                                tail.compareAndSet( t, node )
                            } else {
                                cont.resume( continuee() )
                            }
                    } == continuee() ) {
                        continue
                    }
                    h = head.value
                    if ( node == h.next.value ) {
                        head.compareAndSet( h, node )
                    }
//                    return node.value!!
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while ( true ) {
            var node = Node<E> (null, null, null )
            var h = head.value
            var t = tail.value
            if ( h == t || t.send != null ) {
                var cur_next = t.next.value
                if ( t == tail.value ) {
                    if ( cur_next != null ) {
                        tail.compareAndSet( t, cur_next )
                    }
                } else {
                    if ( suspendCoroutine { cont ->
                            Node<E>( null, cont, null )
                            if ( t.next.compareAndSet( null, node ) ) {
                                tail.compareAndSet( t, node )
                            } else {
                                cont.resume( continuee() )
                            }
                        } == continuee() ) {
                        continue
                    }
                    h = head.value
                    if ( node == h.next.value ) {
                        head.compareAndSet( h, node )
                    }
                    return node.value!!
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

class Node<E>(val x: E?, var send: Continuation<Unit>?, rec : Continuation<Int>? ) {
    val next = atomic<Node<E>?>(null)
    var value = x
    var senders = send
    var receivers = rec

}

// 0 sender
// 1 receiver