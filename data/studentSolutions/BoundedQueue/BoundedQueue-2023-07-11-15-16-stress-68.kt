package playground

import day1.Queue
import kotlinx.atomicfu.*

private const val CAPACITY = 10

class BoundedQueue<E> : Queue<E> {
    private val buffer = Array(CAPACITY) {
        Cell(it.toLong(), null)
    }
    private val enqueuePos = atomic(0L)
    private val dequeuePos = atomic(0L)

    override fun enqueue(element: E) {
        var pos = enqueuePos.value
        while (true) {
            val cell = buffer[(pos % buffer.size).toInt()]
            val seq = cell.sequence.value
            val dif = seq - pos
            if (dif == 0L) {
                if (enqueuePos.compareAndSet(pos, pos + 1)) {
                    cell.data.value = element
                    cell.sequence.value = pos + 1
                    return
                }
            } else if (dif < 0) {
                error("Can't get here in the test")
            } else {
                pos = enqueuePos.value
            }
        }
    }

    override fun dequeue(): E? {
        var pos = dequeuePos.value
        while (true) {
            val cell = buffer[(pos % buffer.size).toInt()];
            val seq = cell.sequence.value
            val dif = seq - (pos + 1)
            if (dif == 0L) {
                if (dequeuePos.compareAndSet(pos, pos + 1)) {
                    val result = cell.data.value!!
                    cell.sequence.value = pos + buffer.size.toLong()
                    return result
                }
            } else if (dif < 0) {
                return null
            } else {
                pos = dequeuePos.value
            }
        }
    }

    private inner class Cell(sequence: Long, data: E?) {
        val sequence = atomic(sequence)
        val data = atomic(data)
    }
}