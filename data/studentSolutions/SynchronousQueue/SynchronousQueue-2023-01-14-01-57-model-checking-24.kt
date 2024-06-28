import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
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
        val dummy = Node<E>(null, "none")
        head = atomic(dummy)
        tail = atomic(dummy)
    }
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true){
            val t = tail.value
            val h = head.value
            if (t == h || t.isSender()){
                if (enqueue(element, "sender")){
                    while (true){
                        if (h.x == null){
                            return
                        }
                    }
                }
            } else {
                if (dequeue(element) == null){
                    continue
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E{
        while (true){
            val t = tail.value
            val h = head.value
            if (t == h || t.isReceiver()){
//                val value =
                if (enqueue(null, "receiver")){
                    while (true){
                        if (t.x != null){
                            return t.x!!
                        }

                    }
                }
            } else {
                val value = dequeue(null)
                if (value == null){
                    continue
                }
                return value
            }
        }
    }

    fun enqueue(x: E?, role: String): Boolean {
        val newNode = Node(x, role)
        val curTail = tail.value
        if (curTail.next.compareAndSet(null, newNode)) {
            tail.compareAndSet(curTail, newNode)
            return true
        } else {
            tail.compareAndSet(curTail, curTail.next.value!!)
        }
        return false
    }

    fun dequeue(x: E?): E? {
        while (true){
            val curHead = head.value
            if (head.value.next.value == null){
                return null
            }
            val newHead = curHead.next
            newHead.value!!.x = x
            if (head.compareAndSet(curHead, newHead.value!!)){
                return curHead.next.value!!.x
            }
        }
    }

    private class Node<E>(var x: E?, private val nodeRole: String) {
        val next = atomic<Node<E>?>(null)
        fun isSender(): Boolean{
            if (nodeRole == "sender"){
                return true
            }
            return false
        }

        fun isReceiver(): Boolean{
            if (nodeRole == "receiver"){
                return true
            }
            return false
        }
    }
}

//suspend fun main(){
//    val res = suspendCoroutine<Any> sc@ { cont ->
//        if (shouldRetry()) {
//            cont.resume(RETRY)
//            return@sc
//        }
//    }
//    if (res === RETRY) continue
//}