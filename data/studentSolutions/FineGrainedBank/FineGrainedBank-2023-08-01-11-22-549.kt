@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    override fun getAmount(id: Int): Long {
        val account = accounts[id]

        account.lock.lock()
        try {
            return account.amount
        } finally {
            account.lock.unlock()
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }

        account.lock.lock()
        try {
            account.amount += amount
            return account.amount
        } finally {
            account.lock.unlock()
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        check(account.amount - amount >= 0) { "Underflow" }

        account.lock.lock()
        try {
            account.amount -= amount
            return account.amount
        } finally {
            account.lock.unlock()
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }

        val from = accounts[fromId]
        val to = accounts[toId]
        check(amount <= from.amount) { "Underflow" }
        check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }

        val first = if (fromId < toId) from else to
        val second = if (fromId < toId) to else from
        try {
            first.lock.lock()
            try {
                second.lock.lock()
                from.amount -= amount
                to.amount += amount
            } finally {
                second.lock.unlock()
            }
        } finally {
            first.lock.unlock()
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