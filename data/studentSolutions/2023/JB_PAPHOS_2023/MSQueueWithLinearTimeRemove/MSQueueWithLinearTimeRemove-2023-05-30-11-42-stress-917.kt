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
            val curTail = tail.value
            val curTailNext = curTail.next.value
            if (curTailNext == null) {
                val newTail = Node(element)
                if (curTail.next.compareAndSet(null, newTail)) {
                    // TODO: Check whether `cutTail` is logically removed,
                    // TODO: removing it physically if so.
                    if (curTail.extractedOrRemoved.value) curTail.remove()
                    tail.compareAndSet(curTail, newTail)
                    return
                }
            } else {
                tail.compareAndSet(curTail, curTailNext)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value
            if (curHeadNext == null) return null
            if (curHeadNext.extractedOrRemoved.compareAndSet(false, true)) {
                head.compareAndSet(curHead, curHeadNext)
                return curHeadNext.element
            } else {
                head.compareAndSet(curHead, curHeadNext)
            }
        }
    }

    override fun remove(element: E): Boolean {
        while (true) {
            // Get the first node that stores an element.
            var node = head.value
            // Try to find the specified element.
            while (true) {
                // Does `node` contain the specified element?
                if (node.element == element && !node.extractedOrRemoved.value) break
                // Is this the end of the queue?
                val next = node.next.value
                if (next == null) return false
                // Check the next node.
                node = next
            }
            // `node` contains the specified element.
            // Try to remove the node, restarting on failure.
            if (node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests, DO NOT CHANGE IT.
     */
    override fun checkNoRemovedElements() {
        // Traverse the linked list
        var node = head.value
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved.value) {
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

        /**
         * TODO: Both [dequeue] and [remove] should first
         * TODO: mark this node as "extracted or removed".
         */
        val extractedOrRemoved = atomic(false)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            val removed = extractedOrRemoved.compareAndSet(false, true)
            // Get the first node that stores an element.
            while (true) {
                val next = this.next.value ?: return removed
                var prev = head.value
                while (prev.next.value !== this) {
                    prev = prev.next.value ?: return removed
                }
                if (prev.next.compareAndSet(this, next)) {
                    if (next.extractedOrRemoved.value) next.remove()
                    if (prev.extractedOrRemoved.value) prev.remove()
                    return removed
                }
            }
            // TODO: The removal procedure is split into two phases.
            // TODO: First, you need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation returns `true`.
            // TODO: In the second phase, the node should be removed
            // TODO: physically, updating the `next` field of the previous
            // TODO: node to `this.next.value`.
            // TODO: Do not remove `head` and `tail` physically to make
            // TODO: the algorithm simpler. In case a tail node is logically removed,
            // TODO: it will be removed physically by `enqueue(..)`.
            TODO("Implement me!")
        }
    }
}
