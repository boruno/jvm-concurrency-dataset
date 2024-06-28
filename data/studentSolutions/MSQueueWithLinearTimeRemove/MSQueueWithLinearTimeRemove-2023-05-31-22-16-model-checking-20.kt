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
        // TODO: When adding a new node, check whether
        // TODO: the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.
        val newNode = Node(element)
        while (true) {
            val curTail = tail.value


            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)

            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
            }

            if (curTail.extractedOrRemoved) {
                curTail.physicallyRemove()
            }
        }

    }

    override fun dequeue(): E? {
        while (true) {

            val curHead = head.value
            val toDelete = curHead.next.value ?: return null


            if (!toDelete.markExtractedOrRemoved()) {
                head.compareAndSet(curHead, toDelete)
                continue
            }

            if (head.compareAndSet(curHead, toDelete)) {
                return toDelete.element
            }


        }



        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.
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

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(
            expect = false,
            update = true
        )

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            if (!markExtractedOrRemoved())
                return false
            return physicallyRemove()
        }

        fun physicallyRemove() : Boolean {
            val newNext = this.next.value
            if (newNext == null) {
                return false
            }

            var curHead : Node? = head.value
            while (curHead != null && curHead.next.value != this) {
                curHead = curHead.next.value
            }

            if (curHead == null)
                return false

            curHead.next.compareAndSet(this, newNext)

            if (curHead.extractedOrRemoved) {
                curHead.physicallyRemove()
            }
            if (newNext.extractedOrRemoved) {
                newNext.physicallyRemove()
            }

            return true
        }
    }
}
