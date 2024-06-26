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
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    private val retry =Node<E>(null)

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
        val currTail = tail.value
        val currHead = head.value
        if(currTail == currHead || currTail.type == NodeType.SENDER){
            enqueueAndSuspend(currTail, element, NodeType.SENDER)
        }
        else{
            dequeueAndResume(currHead)
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val currTail = tail.value
        val currHead = head.value
        if(currTail == currHead || currTail.type == NodeType.RECIEVER){
            return enqueueAndSuspend(currTail, null, NodeType.RECIEVER) as E
        }
        else{
            return dequeueAndResume(currHead) as E
        }
    }

    suspend fun enqueueAndSuspend(t: Node<E>, x: E?, type: NodeType): E?{
        while(true){
            val currTail = tail.value
            val next = currTail.next.value
            if(next != null){
                tail.compareAndSet(t, next)
            }
            else
            {
                var node: Node<E>?
                val res = suspendCoroutine<Node<E>> sc@{ cont ->
                    node = Node(x,cont,type)
                    if(currTail.next.compareAndSet(next,node)){
                        tail.compareAndSet(t, node!!)
                    }
                    else
                    {
                        cont.resume(retry)
                        return@sc
                    }
                }
                if (res === retry)
                    continue
                else
                {
                    val currHead = head.value
                    if(node == currHead.next.value)
                        head.compareAndSet(currHead, node!!)
                    return res.x
                }
            }
        }
    }

    suspend fun dequeueAndResume(h: Node<E>): E?{
        while(true) {
            val currHead = head.value
            val next = currHead.next.value
            if (next == null){
                continue
            }
            if (head.compareAndSet(h, next)) {
                val cont = next.coroutine
                cont!!.resume(next)
                return next.x
            }
        }
    }
}

class Node<E>(val x: E?, val coroutine: Continuation<Node<E>>? = null, val type: NodeType? = null) {
    val next = atomic<Node<E>?>(null)
}

enum class NodeType{
    SENDER,
    RECIEVER
}