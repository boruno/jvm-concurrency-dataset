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
    // private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    // private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    // private val sendIdx = atomic(0L)
    // private val receiveIdx = atomic(0L)
    private val head: AtomicRef<Coroutine<E>>
    private val tail: AtomicRef<Coroutine<E>>

    init {
        val dummy = Dummy<E>()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val t = tail.value
            val h = head.value
            if (t == h || t is Sender<*>) {
                if (enqueueAndSuspendReceive(element, t)) return
            } else if (dequeueAndResume(h, element)) {
                return
            }
        }
    }

    private fun dequeueAndResume(h: Coroutine<E>, element: E): Boolean {
        val current_head = head.value
        val current_head_next = current_head.next.value
        if (current_head_next is Receiver<*>) {
            if (head.compareAndSet(h, current_head_next)) {
                (current_head_next.cont as Continuation<E>).resume(element)
                return true
            } else {
                return false
            }
        } else {

        }
        return false
    }

    private suspend fun enqueueAndSuspendReceive(element: E, t: Coroutine<E>): Boolean {
        val res = suspendCoroutine<Any?> sc@{ cont ->
            val sender = Sender(element, cont)
            val cur_tail = tail.value
            if ((cur_tail == head.value || cur_tail is Sender<*>)) {
                if (cur_tail.next.compareAndSet(null, sender)) {
                    tail.compareAndSet(cur_tail, sender)
                } else {
                    tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                    cont.resume(null)
                    return@sc
                }
                // else {
                //     tail.compareAndSet(tail.value, tail.value.next.value!!)
                // }
            } else {
                // tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                cont.resume(null)
                return@sc
            }
        }
        return res == Unit
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val h = head.value
            val t = tail.value


            if (t == h || t is Receiver<*>) {
                val res = enqueueAndSuspendReceive(t)
                if (res != null) return res
                continue
            } else {
                val current_head = head.value
                val current_head_next = current_head.next.value
                // if (current_head.next.value == null) continue
                if (current_head_next is Sender<*>) {
                    if (head.compareAndSet(h, current_head_next)) {
                        current_head_next.cont.resume(Unit)
                        return (current_head_next as Sender<*>).x as E
                    } else {
                        continue
                        // head.compareAndSet(current_head, current_head_next)
                    }
                }
            }
        }
    }

    private suspend fun enqueueAndSuspendReceive(t: Coroutine<E>): E? {
        val res = suspendCoroutine sc@{ cont ->
            val receiver: Receiver<E> = Receiver(cont)
            val cur_tail = tail.value // r1
            if ((cur_tail == head.value || cur_tail is Receiver<*>)) {
                if (cur_tail.next.compareAndSet(null, receiver)) {
                    tail.compareAndSet(cur_tail, receiver)
                } else {
                    tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                    cont.resume(null)
                    return@sc
                }
                // else {
                //     tail.compareAndSet(tail.value, tail.value.next.value!!)
                // }
            } else {
                cont.resume(null)
                return@sc
            }
        }
        return res
    }
}

abstract class Coroutine<E> {
    val next = atomic<Coroutine<E>?>(null)
}

data class Sender<E>(var x: E?, var cont: Continuation<Unit>) : Coroutine<E>()

data class Receiver<E>(var cont: Continuation<E>) : Coroutine<E>()

class Dummy<E>() : Coroutine<E>() {

}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS