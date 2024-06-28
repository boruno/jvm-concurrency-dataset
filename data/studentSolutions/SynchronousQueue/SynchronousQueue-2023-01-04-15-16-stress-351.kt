import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.coroutines.*

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {

    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val sendIdx = atomic(0L)
    private val receiveIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val curTail = tail.value
            val s = sendIdx.getAndAdd(1)
            val segment = findSegment(curTail, s / SEGMENT_SIZE)
            moveTailForward(segment)
            if (s < receiveIdx.value) {
                if (segment.cas((s % SEGMENT_SIZE).toInt(), null, element)) {
                    return
                }
                val receiver = segment.get((s % SEGMENT_SIZE).toInt()) as Continuation<E>
                receiver.resume(element)
                return
            } else {
                val res = suspendCoroutine<Any> sc@{ cont ->
                    if (!segment.cas((s % SEGMENT_SIZE).toInt(), null, Pair(cont, element))) {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (res == RETRY) continue
//                return
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val curHead = head.value
            val r = receiveIdx.getAndAdd(1)
            val segment = findSegment(curHead, r / SEGMENT_SIZE)
            moveHeadForward(segment)
            if (r < sendIdx.value) {
                if (segment.cas((r % SEGMENT_SIZE).toInt(), null, BREAK_SIGNAL)) {
                    continue
                }
                val sent = segment.get((r % SEGMENT_SIZE).toInt())
                if (sent is Pair<*, *>) {
                    throw RuntimeException()
                    val (sender, elem) = sent as Pair<Continuation<Unit>, E>
                    sender.resume(Unit)
                    return elem
                }
                return sent as E
            } else {
                val sent = segment.get((r % SEGMENT_SIZE).toInt())
                if (sent is Pair<*, *>) {
                    throw RuntimeException()
                    val (sender, elem) = sent as Pair<Continuation<Unit>, E>
                    sender.resume(Unit)
                    return elem
                }
                if (sent is Int) {
                    throw RuntimeException()
                    return sent as E
                }
                val res = suspendCoroutine sc@ { cont ->
                    if (!segment.cas((r % SEGMENT_SIZE).toInt(), null, cont)) {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (res == RETRY) continue
                return res as E
            }
        }
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var cur = start
        while (cur.id < id) {
            cur.next.compareAndSet(null, Segment(cur.id + 1) )
            cur = cur.next.value!!
        }
        return cur
    }

    private fun moveTailForward(cur: Segment) {
        if (tail.value.id < cur.id) {
            tail.compareAndSet(tail.value, cur)
        }
    }

    private fun moveHeadForward(cur: Segment) {
        if (head.value.id < cur.id) {
            head.compareAndSet(head.value, cur)
        }
    }
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 10
const val BREAK_SIGNAL = 4545454565
const val RETRY = 55656656151