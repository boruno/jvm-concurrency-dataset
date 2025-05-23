@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

//package day3

import kotlinx.atomicfu.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>
    private val index = atomic(0)
    private val operations = ArrayList<String>(100)

    init {
        val dummy = Node<E>(element = null, prev = null, -1)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        // TODO: When adding a new node, check whether
        // TODO: the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.
        operations.add("Enqueue $element")
        while (true) {
            val curTail = tail.value
            val newNode = Node(element, curTail, index.getAndIncrement())
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                if (curTail.extractedOrRemoved) curTail.remove()
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        operations.add("Dequeue")
        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.
        while (true) {
            val currentHead = head.value
            val nextHead = currentHead.next.value ?: return null
            if (head.compareAndSet(currentHead, nextHead) && nextHead.markExtractedOrRemoved())
            {
                currentHead.remove()
//                tail.compareAndSet(currentHead, nextHead)
//                nextHead.prev.value = null
//                currentHead.next.value = null
                return nextHead.element
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        operations.add("Remove $element")
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
//            check(nodeNextPrev != null) {
//                "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue. ==> Head: ${head.value.dump()} ==> Tail: ${tail.value.dump()} ==> Node: ${node.dump()}"
//            }
            check(nodeNextPrev == node) {
                "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}. ==> Head: ${head.value.dump()} ==> Tail: ${tail.value.dump()} ==> Node: ${node.dump()}"
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

        return "HEAD: ${head.value.dumpNext()} TAIL: ${tail.value.dumpPrev()}"
    }

    private class Node<E>(
        var element: E?,
        prev: Node<E>?,
        val id: Int
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
            // First phase: mark node - logical removing
            val result = markExtractedOrRemoved()
            // Second phase: physical removing
            val prevNode = prev.value
            val nextNode = next.value
            if (prevNode != null) {
                prevNode.next.compareAndSet(this, nextNode)
                if (prevNode.extractedOrRemoved) prevNode.remove()
            }
            if (nextNode != null) {
                nextNode.prev.compareAndSet(this, prevNode)
                if (nextNode.extractedOrRemoved) nextNode.remove()
            }
            return result
        }

        override fun toString(): String =
            (if (prev.value == null) "" else "<-") +
            ("($id: $element" + if (extractedOrRemoved) ",extractedOrRemoved)" else ")") +
            (if (next.value == null) "" else "->")

        fun dump() : String = (prev.value?.dumpPrev() ?: "") + toString() + (next.value?.dumpNext() ?: "")

        fun dumpNext() : String = if (next.value == null) toString() else toString() + next.value.toString()

        fun dumpPrev() : String = if (prev.value == null) toString() else prev.value.toString() + toString()
    }
}