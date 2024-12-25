//package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import javax.swing.text.StyledEditorKit.BoldAction

import kotlin.random.Random;

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (tryPushToEliminationArray(x)) {
            return
        }

        do {
            val oldHead = top.value
            val newHead = Node(x, oldHead)
        } while (!top.compareAndSet(oldHead, newHead))
    }

    private fun tryPushToEliminationArray(x: E): Boolean {
        val i = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val cell = eliminationArray[i] // Случайная ячейка
        if (!cell.compareAndSet(null, x)) {
            return false // Ячейка оказалась занятой
        }
        for (time in 0..3) { // Даем четыре итерации, чтобы кто-то забрал значение
            if (cell.compareAndSet(null, null)) {
                return true;
            }
        }
        return !cell.compareAndSet(x, null) // Освобождаем ячейку, если результат CAS - true, то
        // никто исходный x не забрал, результат попытки - false
    }

    private fun tryPopFromEliminationArray(): E? {
        val i = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val cell = eliminationArray[i] // случайная ячейка
        for (time in 0..3) { // Ждем, пока кто-нибудь, возможно, что-то сюда не положит
            val x = cell.getAndSet(null)
            if (x != null) { // Кто-то сюда что-то положил - pop удался
                return x
            }
        }
        return null
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var result = tryPopFromEliminationArray()
        if (result != null) {
            return result
        }
        do {
            val oldHead = top.value ?: return null
            result = oldHead.x
            val newHead = oldHead.next
        } while (!top.compareAndSet(oldHead, newHead))
        return result
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT