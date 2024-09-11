import java.util.concurrent.atomic.AtomicBoolean
import java.util.*
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val FC_ARRAY_SIZE = 6;
    private var fc_lock = AtomicBoolean(false)
    private var fc_array = AtomicReferenceArray<Operation<E>?>(FC_ARRAY_SIZE); //    size = Runtime.getRuntime().availableProcessors()?

    private fun tryLock(): Boolean {
        return fc_lock.compareAndSet(false, true);
    }
    private fun unlock(): Boolean {
        return fc_lock.compareAndSet(true, false);
    }

    private enum class OperationType {
        POLL,
        PEEK,
        ADD
    }

    // операция содержит
    // 1) свой тип
    // 2) полезную нагрузку/параметр (если есть)
    // 3) ответ на операцию
    private class Operation<E> {
        var operationType: OperationType
            private set
        var payload: E?
            private set
        var answer: Answer<E> = Answer()
            private set
        constructor(operationType: OperationType, payload: E? = null){
            this.operationType = operationType
            this.payload = payload;
        }
    }
    // ответ содержит
    // 1) флаг: был ли дан ответ на вопрос
    // 2) сам ответ (если есть)
    private class Answer<E> {
        var answerGiven: Boolean = false
            private set
        var answer: E? = null
            private set
        public fun giveAnswer(answer: E? = null) {
            this.answer = answer;
            answerGiven = true
        }
    }

    // функция выполняет операцию которую ей передают
    private fun executeFunctionFromQueue(op: Operation<E>): E? {
        if (op.operationType == OperationType.POLL) {
            return q.poll()
        }
        else if (op.operationType == OperationType.PEEK) {
            return q.peek()
        }
        else if (op.operationType == OperationType.ADD) {
            q.add(op.payload)
            return op.payload;
        }
        return null;
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
//    fun poll(): E? {
//        var i = Random.nextInt(0, FC_ARRAY_SIZE)
//        while (true) {
//            if (fc_array.compareAndSet(i, null, Operation<E>(OperationType.POLL))) {
//                break
//            }
//            i = Random.nextInt(0, FC_ARRAY_SIZE)
//        }
//        val op = fc_array[i]
//        while (!tryLock()) {
//            if (op == null) continue
//            if (op.answer.answerGiven) {
//                return op.answer.answer
//            }
//        }
//    }
    fun poll(): E? {
        // порядковый номер этой операции в массиве (изначально отсутствует)
        var operation = Operation<E>(OperationType.POLL)
        var operationIdInArray: Int? = null

        // цикл работает если поток не может взять блокировку, внутри сначала
        // происходит попытка поместить операцию в массив если ее еще там нет
        // если она уже есть, то происходит проверка был ли дан ответ или нет
        while (!tryLock()) {
            if (operationIdInArray == null) {
                val i = Random.nextInt(0, FC_ARRAY_SIZE)
                if (fc_array.compareAndSet(i, null, Operation<E>(OperationType.POLL))) {
                    operationIdInArray = i
                }
            }
            if (operationIdInArray == null) continue
            val op = fc_array[operationIdInArray]
            if (op == null) continue
            if (op.answer.answerGiven) {
                return op.answer.answer
            }
        }

        // если потоку удалось взять блокировку
        // выполняем свою операцию
        val answer = q.poll()

        // проходим по всем элементам и помогает завершать операции
        // кроме своей операции - ее он обнуляет/удаляет и скипает итерацию
        for (i in 0 until FC_ARRAY_SIZE) {
            if (i == operationIdInArray) {
                fc_array[operationIdInArray] = null
                continue
            }
            val foundOperation = fc_array[i]
            if (foundOperation != null) {
                foundOperation.answer.giveAnswer(executeFunctionFromQueue(foundOperation))
                fc_array[i] = null
            }
        }
        unlock()

        // в конце возвращаем ответ на свою операцию
        return answer;
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        // порядковый номер этой операции в массиве (изначально отсутствует)
        var operationIdInArray: Int? = null

        // цикл работает если поток не может взять блокировку, внутри сначала
        // происходит попытка поместить операцию в массив если ее еще там нет
        // если она уже есть, то происходит проверка был ли дан ответ или нет
        while (!tryLock()) {
            if (operationIdInArray == null) {
                val i = Random.nextInt(0, FC_ARRAY_SIZE)
                if (fc_array.compareAndSet(i, null, Operation<E>(OperationType.PEEK))) {
                    operationIdInArray = i
                }
            }
            if (operationIdInArray == null) continue
            val op = fc_array[operationIdInArray]
            if (op == null) continue
            if (op.answer.answerGiven) {
                return op.answer.answer
            }
        }

        // если потоку удалось взять блокировку
        // выполняем свою операцию
        val answer = q.peek()

        // проходим по всем элементам и помогает завершать операции
        // кроме своей операции - ее он обнуляет/удаляет и скипает итерацию
        for (i in 0 until FC_ARRAY_SIZE) {
            if (i == operationIdInArray) {
                fc_array[operationIdInArray] = null
                continue
            }
            val foundOperation = fc_array[i]
            if (foundOperation != null) {
                foundOperation.answer.giveAnswer(executeFunctionFromQueue(foundOperation))
                fc_array[i] = null
            }
        }
        unlock()

        // в конце возвращаем ответ на свою операцию
        return answer;
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        // порядковый номер этой операции в массиве (изначально отсутствует)
        var operationIdInArray: Int? = null

        // цикл работает если поток не может взять блокировку, внутри сначала
        // происходит попытка поместить операцию в массив если ее еще там нет
        // если она уже есть, то происходит проверка был ли дан ответ или нет
        while (!tryLock()) {
            if (operationIdInArray == null) {
                val i = Random.nextInt(0, FC_ARRAY_SIZE)
                if (fc_array.compareAndSet(i, null, Operation<E>(OperationType.ADD, element))) {
                    operationIdInArray = i
                }
            }
            if (operationIdInArray == null) continue
            val op = fc_array[operationIdInArray]
            if (op == null) continue
            if (op.answer.answerGiven) {
                return
            }
        }

        // если потоку удалось взять блокировку
        // выполняем свою операцию
        q.add(element);

        // проходим по всем элементам и помогает завершать операции
        // кроме своей операции - ее он обнуляет/удаляет и скипает итерацию
        for (i in 0 until FC_ARRAY_SIZE) {
            if (i == operationIdInArray) {
                fc_array[operationIdInArray] = null
                continue
            }
            val foundOperation = fc_array[i]
            if (foundOperation != null) {
                foundOperation.answer.giveAnswer(executeFunctionFromQueue(foundOperation))
                fc_array[i] = null
            }
        }
        unlock()
    }
}