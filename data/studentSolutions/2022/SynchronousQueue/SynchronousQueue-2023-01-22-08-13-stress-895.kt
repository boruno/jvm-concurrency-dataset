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

    //очередь ресиверов и сендеров
    //если в очереди только сендеры пишем
    //ресивер забирает сендера
    //а сендер ресивера
    init {
        val dummy = Node<E>(null, false) // плевать на тип
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private class Node<E>(var x: E?, val isSender: Boolean) {
        val next = atomic<Node<E>?>(null)
        var cont : Continuation<Boolean>? = null
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        doOperation(element)

        return
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val answer = doOperation()

        return answer!!
    }

    private suspend fun doOperation(element: E? = null): E? {
        val isSender = element != null
        val node = Node(element, isSender)

        while (true) {
            val curHead = head.value
            val nextHead = curHead.next.value
            val curTail = tail.value
            val nextTail = curTail.next.value

            // приводим к нормальному состоянию хвост
            if (nextTail != null) {
                tail.compareAndSet(curTail, nextTail)
                continue
            }

            // пусто или очередь такого же типа
            if (curTail == curHead || curTail.isSender == isSender) {
                if (tryPushBack(curTail, node)) {
                    return node.x
                }
                else {
                    continue
                }
            }

            val nextValue = nextHead?.x
            if (!isSender && nextValue == null) {
                continue
            }

            // теперь варик если очередь из другого типа
            if (nextHead != null &&
                nextHead.isSender != isSender &&
                nextHead.cont != null &&
                head.compareAndSet(curHead, nextHead)) {
                nextHead.x = element
                nextHead.cont!!.resume(true)
                return nextValue
            }
        }
    }

    private suspend fun tryPushBack(curTail: Node<E>, newTail: Node<E>): Boolean {
        return suspendCoroutine { cont ->
            newTail.cont = cont
            if (!curTail.next.compareAndSet(null, newTail)) {
                cont.resume(false)
            }
        }
    }
}