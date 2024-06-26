@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day2

import kotlinx.atomicfu.*

class MSQueueWithLinearTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val current = tail.value
            val next = current.next.value
            if (next != null) {
                tail.compareAndSet(current, next)
                if (current.extractedOrRemoved) current.remove()
                continue
            }
            val newNode = Node(element)
            if (current.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(current, newNode)
                if (current.extractedOrRemoved) current.remove()
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val current = head.value
            val next = current.next.value ?: return null
            val success = next.markExtractedOrRemoved()
            head.compareAndSet(current, next)
            if (success) return next.element
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.value
        while (true) {
            val next = node.next.value
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun checkNoRemovedElements() {
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        var node = head.value
        // Traverse the linked list
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of this queue"
                }
            }
            node = node.next.value ?: break
        }
    }

    private inner class Node(
        var element: E?
    ) {
        val next = atomic<Node?>(null)

        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean = markExtractedOrRemoved().also { if (it) physicallyRemove() }

        private fun physicallyRemove() {
            val previous = findPrevious() ?: return
            physicallyRemove(previous)
        }

        private fun physicallyRemove(previous: Node) {
            if (tail.value === this) return
            if (head.value === this) return
            val next = next.value
            previous.next.compareAndSet(this, next)
            if (previous.extractedOrRemoved) previous.physicallyRemove()
            if (next != null && next.extractedOrRemoved) next.physicallyRemove(previous)
        }

        private fun findPrevious(): Node? {
            var node = head.value
            while (true) {
                val next = node.next.value
                checkNotNull(next)
                if (next === this) return node
                node = next
            }
        }
    }
}
