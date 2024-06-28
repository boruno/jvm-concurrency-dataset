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


    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val sendIdx = atomic(0L)
    private val receiveIdx = atomic(0L)


    private val bot = Bot()
    private val RETRY = "retry"
    private val DONE = "done"

    init {
        val firstNode = Segment(0L)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val i = sendIdx.getAndIncrement()
            if (i < receiveIdx.value) {
                val curHead = head.value
                val s = findSegment(i, curHead)
                moveHeadForward(s)
                val res = suspendCoroutine { cont ->
                    if (!s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element to cont)) {
                        cont.resume(RETRY)
                    }
                }
                if (res != RETRY) {
                    return
                } else {
                    val receive = (s.elements[(i % SEGMENT_SIZE).toInt()].value)
                    if (receive is Bot) {
                        continue
                    } else {
                        (s.elements[(i % SEGMENT_SIZE).toInt()].value as Continuation<E>).resume(element)
                        return
                    }
                }
            } else {
                val curTail = tail.value
                val s = findSegment(i, curTail)
                moveTailForward(s)
                val res = suspendCoroutine<Any> { cont ->
                    if (!s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element to cont)) {
                        cont.resume(RETRY)
                    }
                }
                if (res != RETRY) {
                    return
                }
            }
        }
    }

    suspend fun receive(): E {
        loop1@ while (true) {
            val r = receiveIdx.getAndIncrement()
            if (r < sendIdx.value) {
                val curHead = head.value
                val s = findSegment(r, curHead)
                moveHeadForward(s)
                if (s.elements[(r % SEGMENT_SIZE).toInt()].compareAndSet(null, bot)) {
                    continue@loop1
                } else {
                    val send = s.elements[(r % SEGMENT_SIZE).toInt()].value as Pair<*, *>
                    (send.second as Continuation<Any>).resume(DONE)
                    return send.first as E
                }
            } else {
                val curTail = tail.value
                val s = findSegment(r, curTail)
                moveTailForward(s)
                val res = suspendCoroutine { cont ->
                    if (!s.elements[(r % SEGMENT_SIZE).toInt()].compareAndSet(null, cont)) {

                    } else {
                        val send = s.elements[(r % SEGMENT_SIZE).toInt()].value as Pair<*, *>
                        (send.second as Continuation<Any>).resume(DONE)
                        cont.resume(send.first)
                    }
                }
                return res as E
            }
        }
    }

    private fun findSegment(cid: Long, currentHead: Segment): Segment {
        var current: Segment = currentHead
        for (i in current.id until cid / SEGMENT_SIZE) {
            var next = current.next.value
            if (next == null) {
                val tmp = Segment(i + 1)
                current.next.compareAndSet(null, tmp)
                next = current.next.value
            }
            current = next!!
        }
        return current
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */

    private fun moveTailForward(s: Segment) {
        var curTail = tail.value
        while (curTail.id < s.id) {
            tail.compareAndSet(curTail, s)
            curTail = tail.value
        }
    }

    private fun moveHeadForward(s: Segment) {
        var curHead = head.value
        while (curHead.id < s.id) {
            head.compareAndSet(curHead, s)
            curHead = head.value
        }
    }
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

private class Bot

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
