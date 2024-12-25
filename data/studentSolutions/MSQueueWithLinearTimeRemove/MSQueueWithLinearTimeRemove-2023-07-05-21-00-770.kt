@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

//package day2

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
        val newTail = Node(element)
        while (true) {
            if (tail.value.next.compareAndSet(null, newTail)) {
                actualizeTail()
                return
            }
            // Help out and try again.
            actualizeTail()
        }
    }

    private fun actualizeTail() {
        while (true) {
            val tailCandidate = tail.value
            val tailCandidateNext = tailCandidate.next.value ?: return // tail's next is null => all done
            tail.compareAndSet(tailCandidate, tailCandidateNext)
            tailCandidate.eraseIfNeeded()
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val expectedHead = head.value
            val newHead = expectedHead.next.value ?: return null
            if (head.compareAndSet(expectedHead, newHead)) {
                if (newHead.markExtractedOrRemoved())
                    return newHead.element
            }
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

    // TODO: Node is an inner class for accessing `head` in `remove()`
    private inner class Node(
        var element: E?
    ) {
        val next = atomic<Node?>(null)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            if (!markExtractedOrRemoved())
                return false
            erase()
            return true
        }

        fun eraseIfNeeded() {
            if (extractedOrRemoved)
                erase()
        }

        private fun prev(): Node? {
            var node = this@MSQueueWithLinearTimeRemove.head.value
            while (true) {
                val next = node.next.value ?: return null
                if (next === this)
                    return node
                node = next
            }
        }

        private fun erase() {
            // If this became the head, don't do anything. It'll be physically removed during the next dequeue
            // (by virtue of head becoming head.next and the old head becoming unreferenced).
            if (this === this@MSQueueWithLinearTimeRemove.head.value)
                return
            // If this became the tail, don't do anything. It'll be physically removed during the next enqueue.
            // TODO: What if it becomes the tail halfway through?
            if (this === this@MSQueueWithLinearTimeRemove.tail.value)
                return
            val prev = prev() ?: return // No previous => physically removed
            val next = next.value
            prev.next.value = next
            prev.eraseIfNeeded()
            next?.eraseIfNeeded()
        }
    }
}
