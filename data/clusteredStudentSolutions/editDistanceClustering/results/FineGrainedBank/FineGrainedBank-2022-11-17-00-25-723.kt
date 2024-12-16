package mpp.fgbank


import kotlinx.atomicfu.locks.*
import java.util.concurrent.atomic.AtomicBoolean


class FineGrainedBank(n: Int) {
    private val accounts: Array<Account> = Array(n) { Account() }

    private val calculatingTotal = AtomicBoolean(false)

    /**
     * Returns current amount in the specified account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @return amount in account.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     */
    fun getAmount(index: Int): Long {
        if (index < 0 || index >= accounts.size)
            throw IndexOutOfBoundsException()
        accounts[index].lock.lock()
        while (calculatingTotal.get()) {}
        val amount = accounts[index].amount
        accounts[index].lock.unlock()
        return amount
    }

    /**
     * Returns total amount deposited in this bank.
     */
    fun totalAmount(): Long {
        while (!calculatingTotal.compareAndSet(false, true)) {}
        val sum = accounts.sumOf { it.amount }
        calculatingTotal.set(false)
        return sum
    }

    /**
     * Deposits specified amount to account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @param amount positive amount to deposit.
     * @return resulting amount in account.
     * @throws IllegalArgumentException when amount <= 0.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     * @throws IllegalStateException when deposit will overflow account above [MAX_AMOUNT].
     */
    fun deposit(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        if (index < 0 || index >= accounts.size)
            throw IndexOutOfBoundsException()
        val account = accounts[index]
        try {
            account.lock.lock()
            while (calculatingTotal.get()) {}
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            return account.amount
        } catch (e: IllegalStateException) {
            throw e
        } finally {
            account.lock.unlock()
        }
    }

    /**
     * Withdraws specified amount from account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @param amount positive amount to withdraw.
     * @return resulting amount in account.
     * @throws IllegalArgumentException when amount <= 0.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     * @throws IllegalStateException when account does not enough to withdraw.
     */
    fun withdraw(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        if (index < 0 || index >= accounts.size)
            throw IndexOutOfBoundsException()
        val account = accounts[index]
        try {
            account.lock.lock()
            while (calculatingTotal.get()) {}
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        } catch (e: IllegalStateException) {
            throw e
        } finally {
            account.lock.unlock()
        }
    }

    /**
     * Transfers specified amount from one account to another account.
     *
     * @param fromIndex account index to withdraw from.
     * @param toIndex account index to deposit to.
     * @param amount positive amount to transfer.
     * @throws IllegalArgumentException when amount <= 0 or fromIndex == toIndex.
     * @throws IndexOutOfBoundsException when account indices are invalid.
     * @throws IllegalStateException when there is not enough funds in source account or too much in target one.
     */
    fun transfer(fromIndex: Int, toIndex: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromIndex != toIndex) { "fromIndex == toIndex" }
        if (fromIndex < 0 || fromIndex >= accounts.size || toIndex < 0 || toIndex >= accounts.size)
            throw IndexOutOfBoundsException()
        val from = accounts[fromIndex]
        val to = accounts[toIndex]
        try {
            if (fromIndex <= toIndex) {
                from.lock.lock()
                to.lock.lock()
            } else {
                to.lock.lock()
                from.lock.lock()
            }
            while (calculatingTotal.get()) {}
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
        } catch (e: IllegalStateException) {
            throw e
        } finally {
            from.lock.unlock()
            to.lock.unlock()
        }
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0
        val lock = ReentrantLock()
    }
}

/**
 * The maximal amount that can be kept in a bank account.
 */
private const val MAX_AMOUNT = 1000000000000000L
