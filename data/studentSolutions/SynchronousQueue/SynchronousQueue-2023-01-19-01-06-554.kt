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
            var t = tail
            var h = head
            if ( t.value == h.value || t.value.receivers != null ) {
                return suspendCoroutine<Unit> { cont ->
                    enqueue( Node<E>(element, cont, null ) )
                 }
            } else {
                var a = dequeue()
                var s = a!!.receivers!!
                s.resume( element as Int )
            }
        }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        var t = tail
        var h = head
        if ( t.value == h.value || t.value.senders != null ) {
            return suspendCoroutine<Int> { cont ->
                enqueue( Node<E>(null as E?, null, cont ) )
            } as E
        } else {
            var a = dequeue()
            var s = a!!.senders!!
            s.resume( Unit )
            return a.x!!
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
            if ( cur_head.next.value == null ) {
                return null
            } else {
                val cur_head_next = cur_head.next.value
                if ( head.compareAndSet( cur_head, cur_head_next!! ) ) {
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