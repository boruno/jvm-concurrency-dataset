@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }


    private val locks = mutableMapOf<Int, ReentrantLock>()

    override fun getAmount(id: Int): Long {
        val lock = locks.putIfAbsent(id, ReentrantLock())!!
        // TODO: Make this operation thread-safe via fine-grained locking.
        lock.lock()
        try {
            val account = accounts[id]
            return account.amount
        } finally {
            lock.unlock()
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        val lock = locks.putIfAbsent(id, ReentrantLock())!!
        lock.lock()

        try {


            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[id]
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            return account.amount
        } finally {
            lock.unlock()
        }

    }

    override fun withdraw(id: Int, amount: Long): Long {
        val lock = locks.putIfAbsent(id, ReentrantLock())!!
        lock.lock()
        try {

            // TODO: Make this operation thread-safe via fine-grained locking.
            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[id]
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        } finally {
            lock.unlock()
        }

    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        val fromLock = locks.putIfAbsent(fromId, ReentrantLock())!!
        val toLock = locks.putIfAbsent(toId, ReentrantLock())!!
        fromLock.lock()
        toLock.lock()
        try {

            // TODO: Make this operation thread-safe via fine-grained locking.
            require(amount > 0) { "Invalid amount: $amount" }
            require(fromId != toId) { "fromId == toId" }
            val from = accounts[fromId]
            val to = accounts[toId]
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
        } finally {
            toLock.lock()
            fromLock.lock()

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