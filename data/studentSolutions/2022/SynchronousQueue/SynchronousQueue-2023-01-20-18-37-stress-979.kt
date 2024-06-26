import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    val RETRY = Node<E>(null)
    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val currHead = head.value
            val currTail = tail.value
            if (currTail == currHead || !currTail.isReciever()) {
                val next = currTail.next.value
                if (next != null)
                    tail.compareAndSet(currTail, next)
                else {
                    var node: Node<E>
                    val res = suspendCoroutine sc@{ cont ->
                        node = Node(element, cont)
                        if (currTail.next.compareAndSet(null, node)) {
                            tail.compareAndSet(currTail, node)
                        } else {
                            cont.resume(RETRY)
                            return@sc
                        }
                    }
                    if (res === RETRY)
                        continue
                    if (node == head.value.next.value) {
                        head.compareAndSet(head.value, node)
                    }
                    return
                }
            } else {
                val curr_head = head.value
                val curr_tail = tail.value
                val next = curr_head.next.value
                if(curr_head != head.value || curr_tail != tail.value || next == null)
                    continue
                if(head.compareAndSet(curr_head, next))
                {
                    val cont = next.continuation
                    cont!!.resume(Node(element))
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
        while(true) {
            val currTail = tail.value
            val currHead = head.value
             if (currTail == currHead || currTail.isReciever()) {
                 val next = currTail.next.value
                 if(next != null)
                     tail.compareAndSet(currTail, next)
                 else {
                     var node: Node<E>
                     val res = suspendCoroutine sc@{ cont ->
                         node = Node(null, cont)
                         if (currTail.next.compareAndSet(null, node)) {
                             tail.compareAndSet(currTail, node)
                         } else {
                             cont.resume(RETRY)
                             return@sc
                         }
                     }
                     if (res === RETRY)
                         continue
                     if(node == head.value.next.value)
                     {
                         head.compareAndSet(head.value, node)
                     }
                     return res.x!!
                 }
            } else {
                 val curr_head = head.value
                 val curr_tail = tail.value
                 val next = curr_head.next.value
                 if(curr_head != head.value || curr_tail != tail.value || next == null)
                     continue
                 if(head.compareAndSet(curr_head, next))
                 {
                     val cont = next.continuation
                     cont!!.resume(Node(null))
                     return next.x!!
                 }
             }
        }
    }

    //suspend fun enqueueSuspend(x: E?): E?{
    //    while(true){
    //        val curr_tail = tail.value
    //        val next = curr_tail.next.value
    //        if(next != null)
    //            tail.compareAndSet(curr_tail, next)
    //        else {
    //            var node: Node<E>
    //            val res = suspendCoroutine sc@{ cont ->
    //                 node = Node(x, cont)
    //                if (curr_tail.next.compareAndSet(null, node)) {
    //                    tail.compareAndSet(curr_tail, node)
    //                } else {
    //                    cont.resume(RETRY)
    //                    return@sc
    //                }
    //            }
    //            if (res === RETRY)
    //                continue
    //            if(node == head.value.next.value)
    //            {
    //                head.compareAndSet(head.value, node)
    //            }
    //            return res.x
    //        }
    //    }
    //}
//
    //fun dequeueResume(x: E?): E?{
    //    while(true){
    //        val curr_head = head.value
    //        val curr_tail = tail.value
    //        val next = curr_head.next.value
    //        if(curr_head != head.value || curr_tail != tail.value || next == null)
    //            continue
    //        if(head.compareAndSet(curr_head, next))
    //        {
    //            val cont = next.continuation
    //            cont!!.resume(Node(x))
    //            return next.x
    //        }
//
    //    }
    //}

}

class Node<E>(val x: E?, val continuation: Continuation<Node<E>>? = null) {
    val next = atomic<Node<E>?>(null)

    fun isReciever(): Boolean{
        return x == null
    }
}