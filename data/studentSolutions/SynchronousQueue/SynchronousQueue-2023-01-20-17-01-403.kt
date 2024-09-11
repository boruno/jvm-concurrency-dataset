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

    init {
        val dummy = Node<E>(null, null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val currTail = tail.value
        val currHead = head.value
        if(currTail == currHead || !currTail.isReciever()){
            enqueueSuspend(element)
        }
        else
            dequeueResume()
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val currTail = tail.value
        val currHead = head.value
        if(currTail == currHead || currTail.isReciever()){
            return enqueueSuspend(null) as E
        }
        else
            return dequeueResume() as E
    }

    suspend fun enqueueSuspend(x: E?): Any?{
        while(true){
            val curr_tail = tail.value
            val next = curr_tail.next.value
            if(next != null)
                tail.compareAndSet(curr_tail, next)
            val res = suspendCoroutine sc@{ cont ->
                val node = Node(x,cont)
                if (curr_tail.next.compareAndSet(null, node)) {
                    tail.compareAndSet(curr_tail, node)
                } else {
                    cont.resume(null)
                    return@sc
                }
            }
            if(res == null)
                continue
            return res
        }
    }

    suspend fun dequeueResume(): Any?{
        while(true){
            val curr_head = head.value
            val cont = curr_head.continuation
            cont!!.resume(curr_head.x!!)
            val curr_head_next = curr_head.next.value ?: return null
            if(head.compareAndSet(curr_head, curr_head_next))
                return curr_head_next.x
        }
    }

}

private class Node<E>(val x: E?, val continuation: Continuation<E>?) {
    val next = atomic<Node<E>?>(null)

    fun isReciever(): Boolean{
        return x == null
    }
}