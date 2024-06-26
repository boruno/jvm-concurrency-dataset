@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day2

import java.util.concurrent.atomic.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node>
    private val tail: AtomicReference<Node>

    init {
        val dummy = Node(element = null, prev = null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        // TODO: When adding a new node, check whether
        // TODO: the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.

        val newNode = Node(element, null)
        while (true) {
            val curTailNode = tail.get()
            newNode.prev.set(curTailNode)

            var toReturn = false
            if (curTailNode.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTailNode, newNode)
                toReturn = true
            } else {
                tail.compareAndSet(curTailNode, curTailNode.next.get())
            }

            if (curTailNode.extractedOrRemoved) {
                removeNodePhysicallyThreadUnsafe(curTailNode)
            }

            if (toReturn) return
        }
    }

    override fun dequeue(): E? {
        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.

        while (true) {
            val curHead = head.get()
            if (curHead == null) throw NullPointerException()
            val headNext = curHead.next.get()
            if (headNext == null) return null
            if (head.compareAndSet(curHead, headNext)) {
                if (headNext.markExtractedOrRemoved()) {
                    val r = headNext.element
                    headNext.element = null  // memory-leak avoiding
                    headNext.prev.set(null)  // memory-leak avoiding
                    return r
                }
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.get()
        while (true) {
            val next = node.next.get()
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }


    private fun removeNodePhysicallyThreadUnsafe(node: Node) {
        if (node == tail.get()) return

        val nodePrev = node.prev.get()
        if (nodePrev == null || nodePrev.extractedOrRemoved) return

        val nodeNext = node.next.get()
        if (nodeNext == null) return
        nodePrev.next.set(nodeNext)
        if (nodeNext.extractedOrRemoved) removeNodePhysicallyThreadUnsafe(nodeNext)
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun validate() {
        check(head.get().prev.get() == null) {
            "`head.prev` must be null"
        }
        check(tail.get().next.get() == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.get()
        while (true) {
            if (node !== head.get() && node !== tail.get()) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.get()
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.get()
            check(nodeNextPrev != null) {
                "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue"
            }
            check(nodeNextPrev == node) {
                "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}"
            }
            // Process the next node.
            node = nodeNext
        }
    }

    private inner class Node(
        var element: E?,
        prev: Node?
    ) {
        val next = AtomicReference<Node?>(null)
        val prev = AtomicReference(prev)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved
            get() =
                _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            // TODO: As in the previous task, the removal procedure is split into two phases.
            // TODO: First, you need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true` at the end.
            // TODO: In the second phase, the node should be removed
            // TODO: physically, updating the `next` field of the previous
            // TODO: node to `this.next.value`.
            // TODO: In this task, you have to maintain the `prev` pointer,
            // TODO: which references the previous node. Thus, the `remove()`
            // TODO: complexity becomes O(1) under no contention.
            // TODO: Do not remove physical head and tail of the linked list;
            // TODO: it is totally fine to have a bounded number of removed nodes
            // TODO: in the linked list, especially when it significantly simplifies
            // TODO: the algorithm.

            if (!markExtractedOrRemoved()) return false

            removeNodePhysicallyThreadUnsafe(this)

            return true
        }
    }
}