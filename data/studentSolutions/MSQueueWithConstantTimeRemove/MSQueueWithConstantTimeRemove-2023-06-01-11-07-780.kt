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
        // TODO: When adding a new node, check whether
        // TODO: the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.
        while (true) {
            val currTailNode = tail.value
            val node = Node(element, currTailNode)
            // try adding a new node to the tail of the queue
            if (currTailNode.next.compareAndSet(null, node)) {
                // TODO comment
                if (currTailNode.extractedOrRemoved) currTailNode.remove()
                // if successful, we need to try updating the tail reference as well
                // (this CAS might fail because another thread might help us out here, but it's okay)
                tail.compareAndSet(currTailNode, node)
                return
            } else {
                // if failed, we want to try helping the other thread out with updating the tail reference,
                // so we can proceed with our operation ourselves
                tail.compareAndSet(currTailNode, currTailNode.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.
        while (true) {
            val currHeadNode = head.value
            val elementNode = currHeadNode.next.value ?: return null
            if (head.compareAndSet(currHeadNode, elementNode)) {
                // TODO comment
                elementNode.prev.value = null
                // TODO comment
                if (!elementNode.markExtractedOrRemoved()) continue
                return elementNode.element
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
                    "<${(node.prev.value ?: Node("X", null)).element}" +
                    " <- ${node.element}${if (node.extractedOrRemoved) ", extractedOrRemoved" else ""} -> " +
                    "${(node.next.value ?: Node("X", null)).element}>"
            // Process the next node.
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
        // TODO comment
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

            // TODO comment
            val wasLogicallyRemoved = markExtractedOrRemoved()

            while (true) {
                val prevNode = prev.value
                val nextNode = next.value
                val prevNodeControl = prev.value
                if (prevNode !== prevNodeControl) continue
                if (prevNode != null && nextNode != null) {
                    if (prevNode.next.compareAndSet(this, nextNode)) continue
                    if (prevNode === prev.value) {
                        if (nextNode.prev.compareAndSet(this, prevNode)) continue
                        if (prevNode.extractedOrRemoved) prevNode.remove()
                    }
                    if (nextNode.extractedOrRemoved) nextNode.remove()
                    break
                }
            }

//            val prevNode = prev.value
//            val nextNode = next.value
//            if (prevNode != null && nextNode != null) {
//                prevNode.next.compareAndSet(this, nextNode)
//                if (prevNode === prev.value) {
//                    nextNode.prev.compareAndSet(this, prevNode)
//                    if (prevNode.extractedOrRemoved) prevNode.remove()
//                }
//                if (nextNode.extractedOrRemoved) nextNode.remove()
//            }

            return wasLogicallyRemoved
        }
    }
}