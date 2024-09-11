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
            if ( tail.value == head.value || tail.value.send != null ) {
                var cor = suspendCoroutine<Unit> { cont ->
                    Node<E>(element, cont, null)
                    var t = tail
                    var h = head
                    if (t.value == h.value || t.value.send != null) {
                        var node = Node<E>(element, cont, null)
                        while (true) {
                            val next_tail = node
                            val cur_tail = tail.value
                            if (cur_tail.next.compareAndSet(null, next_tail)) {
                                if (tail.compareAndSet(cur_tail, next_tail)) {
                                    break
                                } else {
                                    cont.resume( Unit )
                                }
                            } else {
                                tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                            }
                        }
                    } else {
                        cont.resume(Unit)
                    }
                }
//                return cor
            } else {
                if ( tail.value.send == null ) {
                    throw  ExceptionInInitializerError()
                }
                var a = dequeue()
                var s = a!!.receivers!!
                s.resume(element as Int)
            }
        }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true ) {
            var t = tail
            var h = head
            if (t.value == h.value || t.value.receivers != null) {
                var cor = suspendCoroutine<Int> { cont ->
                    Node<E>(null, null, cont)
                    var t = tail
                    var h = head
                    if (t.value == h.value || t.value.send != null) {
                        var node = Node<E>( null, null, cont)
                        while (true) {
                            val next_tail = node
                            val cur_tail = tail.value
                            if (cur_tail.next.compareAndSet(null, next_tail)) {
                                if (tail.compareAndSet(cur_tail, next_tail)) {
                                    break
                                } else {
                                    cont.resume( Unit as Int )
                                }
                            } else {
                                tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                            }
                        }
                    } else {
                        cont.resume(Unit as Int)
                    }
                }
//                return result as E
            } else {
                var a = dequeue() ?: continue
                var s = a.senders!!
                s.resume(Unit)
                return a.x!!
            }
        }
    }

    fun enqueue(x: Node<E>) {
        while ( true ) {
            val next_tail = x
            val cur_tail = tail.value
            if ( cur_tail.next.compareAndSet( null, next_tail ) ) {
                tail.compareAndSet( cur_tail, next_tail )
                return
            } else {
                tail.compareAndSet( cur_tail, cur_tail.next.value!! )
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
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


class Node<E>(val x: E?, val send: Continuation<Unit>?, rec : Continuation<Int>? ) {
    val next = atomic<Node<E>?>(null)
    val value = x
    val senders = send
    val receivers = rec

}

// 0 sender
// 1 receiver