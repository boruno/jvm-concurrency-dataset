@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

//package day3

import kotlinx.atomicfu.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        val node = Node(element, tail.value)
        while (true) {
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, node)) {
                updateTailAndRemoveIfNeeded(curTail, node)
                return
            } else {
                val next = curTail.next.value
                if (next != null) {
                    updateTailAndRemoveIfNeeded(curTail, next)
                }
            }
        }
    }

    private fun updateTailAndRemoveIfNeeded(curTail: Node<E>, next: Node<E>) {
        tail.compareAndSet(curTail, next)
        if (curTail.extractedOrRemoved) curTail.removePhysically()
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, curHeadNext) && curHeadNext.markExtractedOrRemoved()) {
                return curHeadNext.element
            }
        }
    }

    override fun remove(element: E): Boolean {
        var node = head.value
        while (true) {
            val next = node.next.value
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    override fun checkNoRemovedElements() {
        check(head.value.prev.value == null) {
            "`head.prev` must be null"
        }
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        var node = head.value
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.value
            if (nodeNext == null) break
            node = nodeNext
        }
    }

    override fun toString(): String {
        var node = head.value
        if (tail.value.next.value === node) {
            node = tail.value
        }
        val nodes = arrayListOf<String>()
        while (true) {
            nodes += (if (head.value === node) "HEAD = " else "") +
                    (if (tail.value === node) "TAIL = " else "") +
                    "<${node.element}" +
                    (if (node.extractedOrRemoved) ", extractedOrRemoved" else "") +
                    ">"
            node = node.next.value ?: break
        }
        return nodes.joinToString(", ")
    }

    private class Node<E>(
            var element: E?,
            prev: Node<E>?
    ) {
        val next = atomic<Node<E>?>(null)
        val prev = atomic(prev)
        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(expect = false, update = true)

        fun remove(): Boolean {
            if (markExtractedOrRemoved()) {
                removePhysically()
                return true
            }
            return false
        }

        fun removePhysically() {
            if (this.next.value == null || this.next.value == null) return

            val prevNode = findPrev() ?: return
            val nextNode = this.next.value

            prevNode.next.getAndSet(nextNode)
            if (prevNode.extractedOrRemoved) prevNode.removePhysically()
            if (nextNode != null && nextNode.extractedOrRemoved) nextNode.removePhysically()
        }

        private fun findPrev(): Node<E>? {
            return prev.value
        }
    }
}
