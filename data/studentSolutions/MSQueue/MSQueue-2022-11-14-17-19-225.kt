package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E)
    {
        var newTail = Node(x);
        while (true){
            var tmpTail = tail.value;
            if (tmpTail.next.compareAndSet(null, newTail))
            {
                /*
                Если T указывает на последний добавленный элемент и 
                получилось добавить ещё один элемент в хвост, 
                пробуем передвинуть T. Если не получилось передвинуть T,
                значит, другой поток сделал это за нас, завершаем работу.
                Если получилось - то мы сами передвинули T, завершаем работу
                */
                tail.compareAndSet(tmpTail, newTail)
                return;
            }

            else
            {
                /*
                Если T - не последний добавленный элемент элемент, то передвигаем T на последний элемент
                Если этого сделать не получилось, значит, это сделал другой поток.
                Если получилось - значит, наш поток передвинул T на текущий последний элемент.
                В любом случае, возвращаемся в начало CAS-цикла, чтобы завершить добавление в очередь новой вершины.
                */
                if(tmpTail.next.value == null)
                    continue;
                tail.compareAndSet(tmpTail, tmpTail.next.value as Node<E>);
            }

        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E?
    {
        if (head.value.next.value == null)
            return null;
        head.value = head.value.next.value as Node<E>;
        return head.value.x; //H - новый фиктивный элемент
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null;
    }
}

private class Node<E>(var x: E?) {
    val next = atomic<Node<E>?>(null)
}