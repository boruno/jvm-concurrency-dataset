@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    private fun <T> doUnderAccountLock(account: Account, tryLock: Boolean = false, action: ()->T) : T {
        val lock = account.lock
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        val account = accounts[id]
        return doUnderAccountLock(account) {
            account.amount
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        return doUnderAccountLock(account) {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            account.amount
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        return doUnderAccountLock(account) {
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            account.amount
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }

        val from = accounts[fromId]
        val to = accounts[toId]
        while (true) {
            if (!from.lock.tryLock()) {
                Thread.sleep(1)
            } else if (!to.lock.tryLock()) {
                from.lock.unlock()
                Thread.sleep(1)
            }
        }
        try {
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
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

        /**
         * TODO: use this mutex to protect the account data.
         */
        val lock = ReentrantLock()
    }
}