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
        val offer = Sender(element, null)
        while (true) {
            val t = tail.value
            val h = head.value
            if (t == h || t is Sender<*>) {
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else {
                        val res = suspendCoroutine<Any?> sc@{ cont ->
                            offer.cont = cont
                            if (t.next.compareAndSet(null, offer)) {
                                tail.compareAndSet(t, offer)
                            } else {
                                cont.resume(null)
                                return@sc
                            }
                        }
                        // if (res != Unit) continue
                        val h = head.value
                        if (offer == h.next.value) {
                            head.compareAndSet(h, offer)
                        }
                        return
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || head.value != h || n == null) {
                    continue
                }
                val success = (n as Receiver<E?>).element.compareAndSet(null, element)
                head.compareAndSet(h, n)
                if (success) {
                    n.cont!!.resume(element)
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
        while (true) {
            val t = tail.value
            val h = head.value
            if (t == h || t is Receiver<*>) {
                val n = t.next.value
                if (t == tail.value) {
                    if (n != null) {
                        tail.compareAndSet(t, n)
                    } else {
                        val receiver: Receiver<E> = Receiver(null)
                        val res = suspendCoroutine<E> sc@{ cont ->
                            receiver.cont = cont
                            if (t.next.compareAndSet(null, receiver)) {
                                tail.compareAndSet(t, receiver)
                            } else {
                                cont.resume(null as E)
                                return@sc
                            }
                        }
                        if (res != Unit) continue
                        val h = head.value
                        if (receiver == h.next.value) {
                            head.compareAndSet(h, receiver)
                        }
                        return receiver.element.value!!
                    }
                }
            } else {
                val n = h.next.value
                if (t != tail.value || head.value != null || n == null) {
                    continue
                }
                val element = (n as Sender<E?>).e
                val success = (n as Sender<E?>).element.compareAndSet(element, null)
                head.compareAndSet(h, n)
                if (success) {
                    n.cont!!.resume(Unit)
                    return element!!
                }
            }
        }
    }
    // while (true) {
    //     val h = head.value
    //     val t = tail.value
    //
    //     if (t == h || t is Receiver<*>) {
    //         val res = enqueueAndSuspendReceive(t)
    //         if (res != null) return res
    //         continue
    //     } else {
    //         val current_head = head.value
    //         val current_head_next = current_head.next.value
    //         // if (current_head.next.value == null) continue
    //         if (current_head_next is Sender<*>) {
    //             if (head.compareAndSet(h, current_head_next)) {
    //                 current_head_next.cont.resume(Unit)
    //                 return (current_head_next as Sender<*>).x as E
    //             } else {
    //                 continue
    //                 // head.compareAndSet(current_head, current_head_next)
    //             }
    //         }
    //     }
    // }
    // }

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

    data class Sender<E>(var e: E, var cont: Continuation<Unit>?) : Coroutine<E>() {
        val element: AtomicRef<E?> = atomic(null)
    }

    data class Receiver<E>(var cont: Continuation<E>?) : Coroutine<E>() {
        val element: AtomicRef<E?> = atomic(null)
    }

    class Dummy<E>() : Coroutine<E>() {

    }

    const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS