@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day2

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
        while (true) {
            val currentTailNode = tail.value
            val newTailNode = Node(element, currentTailNode)
            if (currentTailNode.next.compareAndSet(null, newTailNode)) {
                tail.compareAndSet(currentTailNode, newTailNode)
                if (currentTailNode.extractedOrRemoved)
                    currentTailNode.removePhysically()
                return
            } else {
                tail.compareAndSet(currentTailNode, currentTailNode.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHeadNode = head.value
            val currentHeadNextNode = currentHeadNode.next.value ?: return null

            if (head.compareAndSet(currentHeadNode, currentHeadNextNode)) {
                currentHeadNextNode.prev.value = null
                if (currentHeadNextNode.markExtractedOrRemoved())
                    return currentHeadNextNode.element
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
        check(head.value.prev.value == null) {
            "`head.prev` must be null"
        }
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.value
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.value
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.value
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

    private class Node<E>(
        val element: E?,
        prev: Node<E>?
    ) {
        val next = atomic<Node<E>?>(null)
        val prev = atomic(prev)

        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(expect = false, update = true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            if (!markExtractedOrRemoved()) return false
            removePhysically()
            return true
        }

        fun removePhysically() {
            val prevNode = prev.value ?: return
            val nextNode = next.value ?: return
            nextNode.prev.value = prevNode
            prevNode.next.value = nextNode
//            if (prevNode.extractedOrRemoved)
//                prevNode.removePhysically()
            if (nextNode.extractedOrRemoved)
                nextNode.removePhysically()
        }
    }
}