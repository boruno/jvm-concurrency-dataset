@file:Suppress("DuplicatedCode")

package day1

import day1.Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    override fun getAmount(id: Int): Long {
        val account = accounts[id]

        val lock = account.lock
        lock.lock()
        try {
            return account.amount
        } finally {
            lock.unlock()
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        val lock = account.lock
        lock.lock()
        try {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            return account.amount
        } finally {
            lock.unlock();
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        val lock = account.lock
        lock.lock()
        try {
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        } finally {
            lock.unlock()
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }

        val ids = listOf(fromId, toId).sorted()

        val from = accounts[ids[0]]
        val to = accounts[ids[1]]

        val fromLock = from.lock
        val toLock = to.lock
        fromLock.lock()
        toLock.lock()
        try {
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
        } finally {
            try {
                fromLock.unlock()
            } finally {
                toLock.unlock()
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

        /**
         * TODO: use this mutex to protect the account data.
         */
        val lock = ReentrantLock()
    }
}