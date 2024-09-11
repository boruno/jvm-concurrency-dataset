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

val SEND = "SEND"
val RECEIVE = "receive"
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
        while(true){
            val curTail = tail.value
            val curHead = head.value
            if(curTail == curHead || curTail.operation.value == SEND){
                val node = Node(element, SEND)
                if(mySuspendCoroutine(curTail, node))
                    return
                continue
            }
            else{
                val res = dequeue(curHead) ?: continue
                if(res.operation.value == RECEIVE) {
                    res.element.getAndSet(element)
                    res.cont.value!!.resume(true)
                    return
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E{
        while(true){
            val curTail = tail.value
            val curHead = head.value
            if (curTail == curHead || curTail.operation.value == RECEIVE){
                val node = Node<E>(null, RECEIVE)
                if(mySuspendCoroutine(curTail, node))
                    return node.element.value!!
            } else{
                val res = dequeue(curHead) ?: continue
                if(res.operation.value == SEND) {
                    res.cont.value!!.resume(true)
                    return res.element.value!!
                }}
        }
    }


    private suspend fun mySuspendCoroutine(curTail: Node<E>, curNode: Node<E>): Boolean{
        return suspendCoroutine sc@{ cont ->
            curNode.cont.value = cont
            if (!enqueue(curTail, curNode)) {
                cont.resume(false)
                return@sc
            }
        }
    }

    private fun enqueue(curtail: Node<E>, curNode: Node<E>): Boolean {
        return if (curtail.next.compareAndSet(null, curNode)){
            tail.compareAndSet(curtail, curNode)
            true
        } else {
            tail.compareAndSet(curtail, curtail.next.value!!)
            false
        }
    }
    private fun dequeue(curHead: Node<E>): Node<E>? {
        val nextHead = curHead.next.value ?: return null
        if (head.compareAndSet(curHead, nextHead))
            return nextHead
        return null
    }

}
class Node<E>(x: E?, operation: String?) {
    val next = atomic<Node<E>?>(null)
    val element = atomic(x)
    val operation: AtomicRef<String?> = atomic(operation)
    val cont: AtomicRef<Continuation<Boolean>?> = atomic(null)
}