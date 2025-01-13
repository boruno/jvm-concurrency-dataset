//package day2

import kotlinx.atomicfu.*

class FAABasedQueue<E> : Queue<E> {
    companion object {
        private val CORRUPTED = Any()
        private const val CHUNK_SIZE = 2
    }

    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)
    private val tail : AtomicRef<Chunk<E>>
    private val head : AtomicRef<Chunk<E>>

    init {
        val initChunk = Chunk<E>(0)
        tail = atomic(initChunk)
        head = atomic(initChunk)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val chunk = getOrCreateChunk(curTail, i/CHUNK_SIZE)
            moveTailForward(chunk)
            if (chunk.array[i%CHUNK_SIZE].compareAndSet(null, element))
                return
        }
    }

    private fun moveTailForward(chunk: Chunk<E>) {
        val curTail = tail.value
        if (chunk.id > curTail.id)
            return
        tail.compareAndSet(curTail, chunk)
    }

    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val chunk = getOrCreateChunk(curHead, i/ CHUNK_SIZE)
            moveHeadForward(chunk)
            if (chunk.array[i% CHUNK_SIZE].compareAndSet(null, CORRUPTED))
                continue
            @Suppress("UNCHECKED_CAST")
            return chunk.array[i% CHUNK_SIZE].value as E
        }
    }

    private fun moveHeadForward(chunk: Chunk<E>){
        val curHead = head.value
        if (chunk.id > curHead.id)
            return
        head.compareAndSet(curHead, chunk)
    }

    private fun getOrCreateChunk(chunk: Chunk<E>, i: Int) : Chunk<E> {
        if (chunk.id == i) return chunk
        val nextChunk = Chunk<E>(i)
        return if (chunk.next.compareAndSet(null, nextChunk)) nextChunk else chunk.next.value!!
    }

    private class Chunk<E>(val id: Int) {
        val array = atomicArrayOfNulls<Any>(CHUNK_SIZE)
        val next = atomic<Chunk<E>?>(null)
    }
}