@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day3

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
            val node = Node(element)
            val curTail = tail
            if (curTail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail.value, node)
                if (curTail.value.extractedOrRemoved) {
                    curTail.value.remove()
                }
                return
            } else {
                val next = curTail.value.next.value ?: continue
                tail.compareAndSet(curTail.value, next)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            if (curHead.markExtractedOrRemoved()) {
                val curHeadNext = curHead.next.value ?: return null
                head.compareAndSet(curHead, curHeadNext)
                return curHeadNext.element
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

    /**
     * This is a string representation of the data structure,
     * you see it in Lincheck tests when they fail.
     * DO NOT CHANGE THIS CODE.
     */
    override fun toString(): String {
        // Choose the leftmost node.
        var node = head.value
        if (tail.value.next.value === node) {
            node = tail.value
        }
        // Traverse the linked list.
        val nodes = arrayListOf<String>()
        while (true) {
            nodes += (if (head.value === node) "HEAD = " else "") +
                    (if (tail.value === node) "TAIL = " else "") +
                    "<${node.element}" +
                    (if (node.extractedOrRemoved) ", extractedOrRemoved" else "") +
                    ">"
            // Process the next node.
            node = node.next.value ?: break
        }
        return nodes.joinToString(", ")
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
            // TODO: The removal procedure is split into two phases.
            // TODO: First, you need to mark the node as "extracted or removed".
            return if (markExtractedOrRemoved()) {
                if (next.value != null) {
                    removePhysically()
                }
                true
            } else {
                false
            }
        }

        private fun curPrev(): Node? {
            var node : Node? = head.value
            while (node != null && node != this) {
                node = node.next.value
            }
            return node
        }

        private fun removePhysically() {
            val prevVal = curPrev()
            val nextVal = next.value
            if (prevVal == null || nextVal == null) {
                throw NullPointerException("PDDMain expected no null variables. prevVal: `$prevVal`, nextVal: `$nextVal`")
            }
            prevVal.next.compareAndSet(this, nextVal)
            if (prevVal.extractedOrRemoved) {
                prevVal.removePhysically()
            }
            if (nextVal.extractedOrRemoved) {
                nextVal.removePhysically()
            }
        }
    }
}
