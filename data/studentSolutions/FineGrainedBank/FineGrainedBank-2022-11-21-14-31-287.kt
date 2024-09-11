package mpp.fgbank

import kotlinx.atomicfu.locks.*

class FineGrainedBank(n: Int) {
    private val accounts: Array<Account> = Array(n) { Account() }

    /**
     * Returns current amount in the specified account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @return amount in account.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     */
    fun getAmount(index: Int): Long {
        val account = accounts[index]
//        account.lock()
//        try {
            return account.amount
//        } finally {
//            account.unlock()
//        }
    }

    /**
     * Returns total amount deposited in this bank.
     */
    fun totalAmount(): Long {
        accounts.forEach { account ->
            account.lock()
        }
        try {
            return accounts.sumOf { account ->
                account.amount
            }
        } finally {
            accounts.forEach { account ->
                account.unlock()
            }
        }
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
        val account = accounts[index]
        account.lock()
        try {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            return account.amount
        } finally {
            account.unlock()
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
        val account = accounts[index]
        account.lock()
        try {
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        } finally {
            account.unlock()
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
        val from = accounts[fromIndex]
        val to = accounts[toIndex]
        if (fromIndex < toIndex) {
            from.lock()
            to.lock()
        } else {
            to.lock()
            from.lock()
        }
        try {
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
        } finally {
            if (fromIndex < toIndex) {
                from.unlock()
                to.unlock()
            } else {
                to.unlock()
                from.unlock()
            }
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

        private val lock = reentrantLock()

        fun lock() = lock.lock()

        fun unlock() = lock.unlock()
    }
}

/**
 * The maximal amount that can be kept in a bank account.
 */
private const val MAX_AMOUNT = 1000000000000000L
