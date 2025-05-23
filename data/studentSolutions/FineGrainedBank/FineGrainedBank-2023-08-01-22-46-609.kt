@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import kotlinx.atomicfu.locks.withLock
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        accounts[id].lock.withLock {
            val account = accounts[id]
            return account.amount
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        accounts[id].lock.withLock {
            account.lock.lock()
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            //return account.amount
        }
        return accounts[id].amount
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        accounts[id].lock.withLock {
            account.lock.lock()
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            //return account.amount
        }
        return accounts[id].amount;
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        val from = accounts[fromId]
        val to = accounts[toId]
        if (fromId < toId) {
            from.lock.lock()
            try {
                to.lock.lock()
                try {
                    check(amount <= from.amount) { "Underflow" }
                    check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
                    from.amount -= amount
                    to.amount += amount
                } finally {
                    to.lock.unlock()
                }
            } finally {
                from.lock.unlock()
            }
        }
        else
        {
            to.lock.lock()
            try {
                from.lock.lock()
                try {
                    check(amount <= from.amount) { "Underflow" }
                    check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
                    from.amount -= amount
                    to.amount += amount
                } finally {
                    from.lock.unlock()
                }
            } finally {
                to.lock.unlock()
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