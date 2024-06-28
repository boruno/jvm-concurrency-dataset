@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day2

import kotlinx.atomicfu.*

class MSQueueWithLinearTimeNonParallelRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private fun findPrevious(element: E): Node? {
        var current = head.value
        while (true) {
            val next = current.next.value
            if (next == element) {
                return current
            }
            if (next == null) {
                return null
            }
            current = next
        }
    }

    override fun enqueue(element: E) {
        // TODO: When adding a new node, check whether
        // TODO: the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.
        while (true) {
            val curTail = tail.value
            if (curTail.extractedOrRemoved) {
                curTail.element?.let {
                    findPrevious(it)?.let { previous ->
                        previous.next.compareAndSet(curTail, null)
                        tail.compareAndSet(curTail, previous)
                    }
                }
                continue
            }
            val newItem = Node(element)
            // add the new item to the queue
            if (curTail.next.compareAndSet(null, newItem)) {
                // make the tail point to the new item
                tail.compareAndSet(curTail, newItem)
                return
            } else {
                // help the other threads to progress
                // tail should not have next item, so it is ok to fix it
                val curTailNext = curTail.next.value
                if (curTailNext != null) {
                    tail.compareAndSet(curTail, curTailNext)
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            // head is always dummy
            val dummy = head.value
            // it is the actual head
            val actualHead = dummy.next.value ?: return null
            // the actual head becomes dummy
            if (head.compareAndSet(dummy, actualHead)) {
                if (actualHead.markExtractedOrRemoved()) {
                    return actualHead.element
                }
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
    override fun validate() {
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
            // TODO: The removal procedure is split into two phases.
            // TODO: First, you need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true` at the end.
            // TODO: In the second phase, the node should be removed
            // TODO: physically, updating the `next` field of the previous
            // TODO: node to `this.next.value`.
            // TODO: Do not remove `head` and `tail` physically to make
            // TODO: the algorithm simpler. In case a tail node is logically removed,
            // TODO: it will be removed physically by `enqueue(..)`.

            if (!markExtractedOrRemoved()) return false

            element?.let {
                findPrevious(it)?.let { prev ->
                    return prev.next.compareAndSet(this, this.next.value)
                }
            }
            return true
        }
    }
}
