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
        // TODO: When adding a new node, check whether
        // TODO: the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.
//        TODO("Implement me!")
        val newNode = Node(element) // создали новую ноду
        val currentTail = tail.value // сохранили текущий хвост
        if (currentTail.next.compareAndSet(null, newNode)) { // если пока мы сохранялись никто не подсунул next, то сами подсовываем

            tail.compareAndSet(currentTail, newNode) // перекидываем хвост
            if (currentTail.extractedOrRemoved) currentTail.remove()
            return
        } else { // пока мы сохраняли хвост, кто-то уже добавил новую сущность, но не успел обновить хвост
            tail.compareAndSet(currentTail, currentTail.next.value!!) // обновляем хвост
            enqueue(element)
        }
    }

    override fun dequeue(): E? {
        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.
//        TODO("Implement me!")
        while(true) {
            val currentHead = head.value // сохраняем состояние head (dumb)
            val currentHeadNext = currentHead.next.value // сохраняем состояние следующего - 1st
                ?: return null // список пуст

            if (head.compareAndSet(currentHead, currentHeadNext)) { // сдвигаем указатель head
                // после перекидывания хэда помечаем ноду как удаленную либо если она уже помечена,
                // запускаемся ещё раз
                if (!currentHeadNext.markExtractedOrRemoved()) continue
                return currentHeadNext.element
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
//            TODO("Implement me!")
            val isExtractedOrRemoved = this.markExtractedOrRemoved()
            // second phase
//            val prev = findPrev(this)
            val prev = this.findPrev() ?: return isExtractedOrRemoved
            val next = this.next.value

            if (this == head.value || this.next.value == null) return isExtractedOrRemoved

            if (prev.next.compareAndSet(this, next)) {
                if(prev.extractedOrRemoved) prev.remove()
//                if(next?.extractedOrRemoved == true) next.remove()
            }

            return isExtractedOrRemoved
        }

//        private fun findPrev(currentNode: Node): Node? {
//            var thisNode: Node = head.value
//            var nextNode: Node? = thisNode.next.value
//
//            while(nextNode != null) {
//                if(nextNode == currentNode) return thisNode
//                nextNode = thisNode.next.value
//            }
//            return null
////            return thisNode!!
//        }
        fun Node.findPrev(): Node? {
            var tempPointer: Node = head.value

            while(tempPointer.next.value != this) {
                tempPointer = tempPointer.next.value ?: return null
            }
            return tempPointer
        }

//        fun Node.findPrevious(): Node? {
//            var node = head.value
//            // Traverse the linked list
//            while (true) {
//                if (node !== head.value && node !== tail.value) {
//                    check(!node.extractedOrRemoved) {
//                        "Removed node with element ${node.element} found in the middle of this queue"
//                    }
//                }
//                node = node.next.value ?: break
//            }
//        }
    }
}
