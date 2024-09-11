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
val RECEIVE = "RECEIVE"
class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>
    init {
        val dummy = Node<E>(null, null)
        dummy.operation = RECEIVE
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
            if(curTail == curHead || curTail.operation == SEND){
                val node = Node(element, SEND)
                if(mySuspendCoroutine(curTail, node))
                    return
            }
            else{
                val res = dequeue(curHead) ?: continue
                if (res.operation == RECEIVE) {
                    res.element = element
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
            if (curTail == curHead || curTail.operation == RECEIVE){
                val node = Node<E>(null, RECEIVE)
                if(mySuspendCoroutine(curTail, node))
                    return curTail.next.value?.element ?: throw RuntimeException()
            } else{
                val res = dequeue(curHead) ?: continue
                if (res.operation == SEND) {
                    res.cont.value!!.resume(true)
                    return res.element!!
                }
            }
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
class Node<E>(x: E?, var operation: String?) {
    val next = atomic<Node<E>?>(null)
    var element = x
    val cont: AtomicRef<Continuation<Boolean>?> = atomic(null)
}