import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 * TODO: This class is **NOT** thread-safe.
 */
class IntIntHashMap {
    private val core = atomic(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }

        while (true) {
            val curCore = core.value
            val getResult = curCore.getInternal(key)

            if (getResult == MOVED_VALUE) {
                curCore.next.value!!.getInternal(key)
            }

            return toValue(getResult)
        }
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key   a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     * [Integer.MAX_VALUE] which is reserved.
     */
    fun put(key: Int, value: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            val curCore = core.value
            val oldValue = curCore.putInternal(key, value)

            if (oldValue != NEEDS_REHASH && oldValue != NEEDS_USE_NEW_CORE) {
                return oldValue
            }

            if (oldValue == NEEDS_REHASH) {
                val nextCore = curCore.rehash()
                core.compareAndSet(curCore, nextCore)
            }
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        //val map: IntArray = IntArray(2 * capacity)
        val shift: Int
        val map = atomicArrayOfNulls<Any>(2 * capacity)
        val next: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)

            for (i in 0 until 2 * capacity) {
                if (i % 2 == 0) {
                    map[i].getAndSet(NULL_KEY)
                }
                else {
                    map[i].getAndSet(NULL_VALUE)
                }
            }
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                val mapKey = map[index].value
                if (mapKey == key) {
                    break
                }

                if (mapKey == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
            // found key -- return value
            val mapValue = map[index + 1].value

            if (mapValue is Moved) {
                val nextMapValue = next.value!!.getInternal(key)

                if (nextMapValue > 0) {
                    return nextMapValue
                }

                return mapValue.value
            }

            return mapValue as Int
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                val mapKey = map[index].value
                if (mapKey == key) {
                    break
                }

                if (mapKey == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot

                    if (map[index].compareAndSet(NULL_KEY, key)) {
                        break
                    }
                    else {
                        continue
                    }
                }

                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }

            while (true) {
                val oldValue = map[index + 1].value
                if (oldValue is Moved) {
                    return NEEDS_REHASH
                }

                if (oldValue !is Int) {
                    throw Exception("Как у тебя не инт???")
                }

                if (oldValue == MOVED_VALUE) {
                    return NEEDS_REHASH
                }

                if (map[index + 1].compareAndSet(oldValue, value)) {
                    return oldValue
                }
            }
        }

        fun rehash(): Core {
            next.compareAndSet(null, Core(map.size))

            val nextCore = next.value
            if (nextCore!!.next.value != null) {
                return nextCore
            }

            var index = 0
            while (index < map.size) {
                val mapKey = map[index].value as Int
                val mapValue = map[index + 1].value

                if (mapValue is Int) {
                    // если уже переместили - делать нечего
                    if (mapValue == MOVED_VALUE) {
                        index += 2

                        continue
                    }

                    // если пусто или удалено, на всякий запишем что перенесли, на случай если есть ключ,
                    // а операция записи значения была прервана
                    // в новое ядро бессмысленно переносить
                    // успешно записали? выйдем через перемещенное
                    if (mapValue == NULL_VALUE || mapValue == DEL_VALUE) {
                        map[index + 1].compareAndSet(mapValue, MOVED_VALUE)

                        continue
                    }

                    // пытаемся переложить пару в новое ядро
                    if (isValue(mapValue)) {
                        // не смогли забить очередь на перемещение
                        val moved = Moved(mapValue)
                        if (!map[index + 1].compareAndSet(mapValue, moved)) {
                            continue
                        }

                        val putResult = nextCore.putInternal(mapKey, mapValue)

                        // null, удалено и обычные значения пернулись == все прошло успешно
                        // можно идти дальше
                        if (putResult >= 0) {
                            // даже если false, это обозначает лишь, что кто то другой закончил перемещение
                            map[index + 1].compareAndSet(moved, MOVED_VALUE)

                            index += 2
                        }

                        continue
                    }
                }

                // попытаемся завершить передвижение
                if (mapValue is Moved) {
                    val putResult = nextCore.putInternal(mapKey, mapValue.value)

                    // null, удалено и обычные значения пернулись == все прошло успешно
                    // можно идти дальше
                    if (putResult >= 0) {
                        index += 2

                        // даже если false, это обозначает лишь, что кто то другой закончил перемещение
                        map[index + 1].compareAndSet(mapValue, MOVED_VALUE)
                    }

                    continue
                }
            }

            return nextCore
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
    }

    private class Moved(val value: Int)
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val MOVED_VALUE = -3 // mark for removed value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed
private const val NEEDS_USE_NEW_CORE = -2 // returned to indicate that this core is not actual now

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0