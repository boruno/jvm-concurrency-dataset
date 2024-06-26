import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.io.File
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
        val curTail = tail.value
        val s = sendIdx.getAndAdd(1)
        File("out.txt").appendText("send started " + element + " at " + s + '\n')
        val segment = findSegment(curTail, s / SEGMENT_SIZE)
        moveTailForward(segment)
        if (s < receiveIdx.value) {
            if (segment.cas((s % SEGMENT_SIZE).toInt(), null, element)) {
                File("out.txt").appendText("send " + element + " wrote the value" + '\n')
                return
            }
            if (segment.get((s % SEGMENT_SIZE).toInt()) == BREAK_SIGNAL) {
                File("out.txt").appendText("send " + element + " restarting because BREAK" + '\n')
                send(element)
                return
            }
            val receiver = segment.get((s % SEGMENT_SIZE).toInt()) as Continuation<E>
            File("out.txt").appendText("send " + element + " resuming receiver at " + s + '\n')
            receiver.resume(element)
            return
        } else {
            val res = suspendCoroutine<Any> sc@{ cont ->
                if (!segment.cas((s % SEGMENT_SIZE).toInt(), null, Pair(cont, element))) {
                    cont.resume(RETRY)
                    return@sc
                }
            }
            if (res == RETRY) {
                File("out.txt").appendText("send " + element + " restarting because RETRY" + '\n')
                send(element)
                return
            }
            File("out.txt").appendText("send " + element + " finished" + '\n')
            return
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val curHead = head.value
        val r = receiveIdx.getAndAdd(1)
        File("out.txt").appendText("receive started at " + r + '\n')
        val segment = findSegment(curHead, r / SEGMENT_SIZE)
        moveHeadForward(segment)
        if (r < sendIdx.value) {
            if (segment.cas((r % SEGMENT_SIZE).toInt(), null, BREAK_SIGNAL)) {
                File("out.txt").appendText("receive restarting because BREAK" + '\n')
                return receive()
            }
            val sent = segment.get((r % SEGMENT_SIZE).toInt())
            if (sent is Pair<*, *>) {
                val (sender, elem) = sent as Pair<Continuation<Unit>, E>
                File("out.txt").appendText("receive resuming sender elem " + elem + '\n')
                sender.resume(Unit)
                return elem
            }
            File("out.txt").appendText("receive returning value sent " + sent + '\n')
            return sent as E
        } else {
            val res = suspendCoroutine<Any> sc@ { cont ->
                if (!segment.cas((r % SEGMENT_SIZE).toInt(), null, cont)) {
                    cont.resume(RETRY)
                    return@sc
                }
            }
            if (res == RETRY) {
                File("out.txt").appendText("receive restart because retry at " + r + '\n')
                return receive()
            }
            File("out.txt").appendText("receive return res " + res + '\n')
            return res as E
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

const val SEGMENT_SIZE = 2
const val BREAK_SIGNAL = 4545454565
const val RETRY = 55656656151