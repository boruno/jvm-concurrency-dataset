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
    private val snd: AtomicRef<Segment>
    private val rec: AtomicRef<Segment>
    private val sendIdx = atomic(0L)
    private val receiveIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        snd = atomic(firstNode)
        rec = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    private fun findSegment(start: Segment, idx: Long) : Segment {
        if (start.startIndex.toLong() == idx) {
            return start
        }
        while (true) {
            val oldv = start.next.value;
            var newv = oldv;
            if (oldv == null) {
                newv = Segment(start.startIndex + 1)
            }
            if (start.next.compareAndSet(oldv, newv)) {
                break
            }
        }
        return findSegment(start.next.value!!, idx)
    }
    private fun moveSForward(s : Segment) {
        while (true) {
            val ct = snd
            val ctv = ct.value
            if (ctv != s) {
                if (ct.compareAndSet(ctv, s)) {
                    return
                }
                continue
            }
            return
        }
    }
    private fun moveRForward(s : Segment) {
        while (true) {
            val ct = rec
            val ctv = ct.value
            if (ctv != s) {
                if (ct.compareAndSet(ctv, s)) {
                    return
                }
                continue
            }
            return
        }
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(obj : E) {
        while (true) {
            val cur_send = snd.value
            val i = sendIdx.getAndIncrement()
            val s = findSegment(cur_send, i / SEGMENT_SIZE)
            moveSForward(s)
            if (i < receiveIdx.value) {
                val thing = s.elements[(i % SEGMENT_SIZE).toInt()].getAndSet(EvilThing())
                if (thing == null) {
                    continue
                }
                val th = thing as SendReceive<E>
                th.contReceive!!.resume(false to obj)
                return
            } else {
                val res = suspendCoroutine<Boolean> sc@ { cont ->
                    if (!s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, SendReceive(cont, null, obj))) {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (res == RETRY) {
                    continue
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
        while (true) {
            val cur_rec = rec.value
            val i = receiveIdx.getAndIncrement()
            val s = findSegment(cur_rec, i / SEGMENT_SIZE)
            moveRForward(s)
            if (i < sendIdx.value) {
                val thing = s.elements[(i % SEGMENT_SIZE).toInt()].getAndSet(EvilThing())
                if (thing == null) {
                    continue
                }
                val th = thing as SendReceive<E>
                th.contSend!!.resume(false)
                return th.element!!
            } else {
                val res = suspendCoroutine<Pair<Boolean, E?>> sc@ { cont ->
                    if (!s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, SendReceive(null, cont, null))) {
                        cont.resume(RETRY to null)
                        return@sc
                    }
                }
                if (res.first == RETRY) {
                    continue
                }
                return res.second!!
            }
        }
    }

    class SendReceive<E>(val contSend: Continuation<Boolean>?, val contReceive: Continuation<Pair<Boolean, E?>>?, val element : E?)

    private class Segment(si: Int) {
        val next: AtomicRef<Segment?> = atomic(null)
        val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
        val startIndex : Int = si

        private fun get(i: Int) = elements[i].value
        private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
        private fun put(i: Int, value: Any?) {
            elements[i].value = value
        }
    }
    private class EvilThing {};//i dont even remember what this is
}
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
const val RETRY = true;