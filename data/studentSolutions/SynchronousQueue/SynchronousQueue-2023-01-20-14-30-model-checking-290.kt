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
        val curr_tail = tail.value
        val s = sendIdx.getAndIncrement()
        if (s < receiveIdx.value){
            val segment = findSegment(curr_tail, (s/ SEGMENT_SIZE).toInt())
            val el = segment.elements[((s % SEGMENT_SIZE).toInt())].value as Ref<E>
            val cor = el.coroutine
            cor.resume(el)
            el.x = element
        }
        else
        {
            val res = suspendCoroutine { cont->
                val segment = findSegment(curr_tail, (s/ SEGMENT_SIZE).toInt())
                moveForward(true, segment)
                val el = Ref<E>(element, cont)
                segment.elements[((s % SEGMENT_SIZE).toInt())].compareAndSet(null, el)
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val curr_head= head.value
        val s = receiveIdx.getAndIncrement()
        if (s < sendIdx.value){
            val segment = findSegment(curr_head, (s/ SEGMENT_SIZE).toInt())
            val el = segment.elements[((s % SEGMENT_SIZE).toInt())].value as Ref<E>
            val cor = el.coroutine
            cor.resume(el)
            return el.x!!
        }
        else
        {
            val res = suspendCoroutine { cont->
                val segment = findSegment(curr_head, (s/ SEGMENT_SIZE).toInt())
                moveForward(false, segment)
                val el = Ref<E>(null, cont)
                segment.elements[((s % SEGMENT_SIZE).toInt())].compareAndSet(null, el)
            }
            return res.x!!
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

class Ref<E>(var x: E?, val coroutine: Continuation<Ref<E>>)

const val SEGMENT_SIZE = 2