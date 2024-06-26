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

    private val RETRY = Ref<E>(null,null)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while(true) {
            val curr_tail = tail.value
            val s = sendIdx.getAndIncrement()
            val segment = findSegment(curr_tail, (s/ SEGMENT_SIZE).toInt())
            moveForward(false, segment)
            if (s < receiveIdx.value) {
                var el = segment.elements[(s%SEGMENT_SIZE).toInt()].value
                if(el != null){
                    el = el as Ref<E>
                    el.x = element
                    val continuation = el.coroutine
                    continuation!!.resume(el)
                    return
                }
            } else {
                val res = suspendCoroutine sc@ {
                    continuation ->
                    val Node = Ref(element, continuation)
                    if(!segment.elements[(s%SEGMENT_SIZE).toInt()].compareAndSet(null, Node)){
                        continuation.resume(RETRY)
                        return@sc
                    }
                }
                return
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while(true) {
            val curr_tail = tail.value
            val r = receiveIdx.getAndIncrement()
            val segment = findSegment(curr_tail, (r / SEGMENT_SIZE).toInt())
            moveForward(false, segment)
            if (r < sendIdx.value) {
                var el = segment.elements[(r % SEGMENT_SIZE).toInt()].value
                if (el != null) {
                    el = el as Ref<E>
                    val continuation = el.coroutine
                    continuation!!.resume(el)
                    return el.x!!
                }
            } else {
                val res = suspendCoroutine sc@{ continuation ->
                    val Node = Ref(null, continuation)
                    if (!segment.elements[(r % SEGMENT_SIZE).toInt()].compareAndSet(null, Node)) {
                        continuation.resume(RETRY)
                        return@sc
                    }
                }
                return res.x!!
            }
        }
    }


    fun findSegment(seg: Segment, id: Int): Segment{
        var start = seg
        while(true) {
            if(start.id == id)
                return start
            if(start.next.value == null)
            {
                val new_seg = Segment(start.id+1)
                if(start.next.compareAndSet(null,new_seg))
                    return new_seg
            }
            start = start.next.value!!
        }
    }

    fun moveForward(isHead: Boolean, seg: Segment){
        if(isHead){
            val curr_head = head.value
            if(curr_head == seg)
                return
            if(head.compareAndSet(curr_head, seg))
                return
        }
        else {
            val curr_tail = tail.value
            if (curr_tail.id >= seg.id)
                return
            else
                if (tail.compareAndSet(curr_tail, seg))
                    return
        }
    }
}

class Segment(_id: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    var id = _id;

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

class Ref<E>(var x: E?, val coroutine: Continuation<Ref<E>>?)

const val SEGMENT_SIZE = 2