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
    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>
    private val sendIdx = atomic(0L)
    private val receiveIdx = atomic(0L)
    private val invalidIndex = 0;

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var currentSegment = start
        while(true)
        {
            if (currentSegment.id == id)
                return currentSegment
            if (currentSegment.id > id)
                throw Exception("ERROR")
            if (currentSegment.next.value == null)
            {
                currentSegment.next.compareAndSet(null, Segment(currentSegment.id + 1))
                currentSegment = currentSegment.next.value!!
            }
            else
            {
                currentSegment = currentSegment.next.value!!
            }
        }
    }

    private fun moveHeadForward(s: Segment) {
        val currentHead = head.value
        if (currentHead.id == s.id)
            return
        if (currentHead.id <= s.id)
            head.compareAndSet(currentHead, currentHead.next.value!!)
    }

    private fun moveTailForward(s: Segment) {
        val currentTail = tail.value
        if (currentTail.id == s.id)
            return
        if (currentTail.id <= s.id)
            tail.compareAndSet(currentTail, currentTail.next.value!!)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while(true) {
            val currentTail = tail.value
            val i = sendIdx.getAndAdd(1)
            val s = findSegment(currentTail, i / SEGMENT_SIZE)
            moveTailForward(s)
            if (i < receiveIdx.value) {
                val result = s.get((i % SEGMENT_SIZE.toLong()).toInt())
                if (result == null)
                {
                    if (s.cas((i % SEGMENT_SIZE.toLong()).toInt(), null, element))
                        return
                    else
                        continue
                }
                if (result is Continuation<*>)
                {
                    val resultReciever = result as Continuation<E?>
                    resultReciever.resume(element)
                    return
                }
                throw Exception("Something in array which is not something needed")
            }
            else {
                val res = suspendCoroutine<Int> sc@ { cont ->
                    if (!s.cas((i % SEGMENT_SIZE.toLong()).toInt(), null, cont to element)) { // If CAS is unsuccessful, we need tro restart
                        cont.resume(-1)
                        return@sc
                    }
                }
                if (res == -1) continue
                else return
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while(true) {
            val currentHead = head.value
            val r = receiveIdx.getAndAdd(1)
            val s = findSegment(currentHead, r / SEGMENT_SIZE)
            moveHeadForward(s)
            if (r < sendIdx.value) {
                val result = s.get((r % SEGMENT_SIZE.toLong()).toInt())
                if (result == null) {
                    if (s.cas((r % SEGMENT_SIZE.toLong()).toInt(), null, invalidIndex))
                        continue
                }
                if (result is Pair<*, *>) {
                    val (res, elem) = result as Pair<Continuation<Int>, E>
                    res.resume(0)
                    return elem
                }
                return result as E
            }
            else
            {
                val res = suspendCoroutine<E?> sc@ { cont ->
                    if (!s.cas((r % SEGMENT_SIZE.toLong()).toInt(), null, cont)) { // If CAS is unsuccessful, we need tro restart
                        cont.resume(null)
                        return@sc
                    }
                }
                if (res == null) continue
                else return res
            }
        }
    }
}

private class Segment(segmentId: Long) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val id = segmentId


    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 16