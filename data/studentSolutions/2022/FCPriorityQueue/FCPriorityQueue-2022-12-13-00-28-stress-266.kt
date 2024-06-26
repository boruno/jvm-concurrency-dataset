import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fc_array = atomicArrayOfNulls<Pair<Any?, Any?>>(ARRAY_SIZE) // null, 1-null, 1-x, 2-x, 2-Done
    private val lock = atomic(false)
    private val random = ThreadLocalRandom.current()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     * Извлекает элемент с наивысшим приоритетом
     * и возвращает его как результат этой функции;
     * возвращает `null`, если очередь пуста.
     */
    @Suppress("UNCHECKED_CAST")
    fun poll(): E? {
        lok@ while (!lock.compareAndSet(false, true)) {
            var i = random.nextInt(0,  fc_array.size)
            while (true) {
                if (fc_array[i].compareAndSet(null, Pair(1, null))) {
                    var x = fc_array[i].value
                    while (x?.second == null) {
                        if (lock.compareAndSet(false, true)) { //не поменяли и взяли блокировку выйти из while(23)
                            break@lok
                        }
                        x = fc_array[i].value
                    }
                    //кто-то помог
                    fc_array[i].compareAndSet(x, null) //освобождаем массив
                    return x as E
                }
                i = random.nextInt(0,  fc_array.size) //не вставили в массив
            }
        }

        //залочили
        val e = q.poll()
        help()
        lock.compareAndSet(true, false)
        return e
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     * Возвращает элемент с наивысшим приоритетом
     * или `null`, если очередь пуста.
     */
    fun peek(): E? {
        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     * Добавляет указанный элемент в очередь.
     */
    fun add(element: E) {
        lok@ while (!lock.compareAndSet(false, true)) {
            var i = random.nextInt(0,  fc_array.size)
            while (true) {
                var cur = Pair(2, element)
                if (fc_array[i].compareAndSet(null, cur)) {
                    while (fc_array[i].compareAndSet(cur, cur)) {//не получилось = значит кто-то поменят (там DONE)
                        if (lock.compareAndSet(false, true)) { //не поменяли и взяли блокировку выйти из while(64)
                            break@lok
                        }
                    }
                    //кто-то помог
                    cur = fc_array[i].value as Pair<Int, E>
                    fc_array[i].compareAndSet(cur, null)
                    return
                }
                i = random.nextInt(0,  fc_array.size) //не вставили в массив
            }
        }

        //залочили
        q.add(element)
        help()
        lock.compareAndSet(true, false)
    }

    @Suppress("UNCHECKED_CAST")
    private fun help() {
        var i = 0
        while (i < fc_array.size) {
            val cur = fc_array[i].value
            if (cur != null) {
                if (cur.first == 1) {
                    if (cur.second == null) {
                        fc_array[i].compareAndSet(cur, Pair(1, q.poll()))
                    }
                } else {
                    val x = cur.second
                    if (x != DONE) {
                        q.add(x as E)
                        fc_array[i].compareAndSet(cur, Pair(2, DONE))
                    }
                }
            }
            i++
        }
    }

    companion object {
        private val DONE = Any()
        private const val ARRAY_SIZE = 10
    }
}