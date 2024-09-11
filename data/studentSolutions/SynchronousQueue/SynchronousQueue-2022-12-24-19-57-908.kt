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
    //..ресивер забирает сендера
    //а сендер ресивера

    init {
        val dummy = Node<E>(null, false)
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
        while (true) {
            val node = Node(element, true)
            val curTail = tail.value
            val curHead = head.value
            val nextTail = curTail.next.value

            if (nextTail != null) {
                tail.compareAndSet(curTail, nextTail)
                continue
            }

            // пусто или очередь сендеров => пушим нового сендера
            if (curTail == curHead || curTail.isSender) {
                if (tryPushBack(curTail, node)) {
                    return
                }
                else {
                    continue
                }
            }

            // теперь варик если очередь из получателей вся
            val nextHead = curHead.next.value ?: return

            if (!nextHead.isSender && nextHead.cont != null && head.compareAndSet(curHead, nextHead)) {
                nextHead.x = element
                nextHead.cont!!.resume(true)
                return
            }

            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)

                return
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val node: Node<E> = Node(null, false)

        while (true) {
            val curHead = head.value
            val nextHead = curHead.next.value
            val curTail = tail.value
            val nextTail = curTail.next.value

            if (nextTail != null) {
                tail.compareAndSet(curTail, nextTail)
                continue
            }

            // пусто или очередь ресиверов => пушим нового ресивера
            if (curTail == curHead || !curTail.isSender) {
                if (tryPushBack(curTail, node)) {
                    return node.x!!
                }
                else {
                    continue
                }
            }

            val element = nextHead?.x ?: continue
            if (nextHead.isSender && nextHead.cont != null && head.compareAndSet(curHead, nextHead)) {
                nextHead.x = null
                nextHead.cont!!.resume(true)
                return element
            }
        }
    }

    private suspend fun tryPushBack(curTail: Node<E>, node: Node<E>): Boolean {
        return suspendCoroutine sc@{ cont ->
            node.cont = cont
            if (!curTail.next.compareAndSet(null, node)) {
                cont.resume(false)
                return@sc
            }

            tail.compareAndSet(curTail, node)
        }
    }
}