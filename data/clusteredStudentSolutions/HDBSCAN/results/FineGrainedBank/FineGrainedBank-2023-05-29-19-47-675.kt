@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*
import kotlinx.atomicfu.locks.withLock

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    internal inline fun <T> Account.withAccount(action: Account.() -> T): T {
        return lock.withLock {
            action(this)
        }
    }

    override fun getAmount(id: Int): Long {
        val account = accounts[id]
        return account.withAccount {
            amount
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        return account.withAccount {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            this.amount += amount
            this.amount
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        return account.withAccount {
            check(account.amount - amount >= 0) { "Underflow" }
            this.amount -= amount
            this.amount
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        val from = accounts[fromId]
        val to = accounts[toId]
//        val lhs = from.withAccount { from.amount }
//        val rhs = to.withAccount { to.amount }
//        check(amount <= from.amount) { "Underflow" }
//        check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
//        val resultLhs = lhs - amount
//        val resultRhs = rhs + amount
//        from.withAccount {
//            this.amount = resultLhs
//        }
//        to.withAccount {
//            this.amount = resultRhs
//        }
//        val first = if (fromId > toId)

        if (fromId > toId) {
            from.withAccount {
                to.withAccount {
                    check(amount <= from.amount) { "Underflow" }
                    check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
                    to.amount += amount
                }
                from.amount -= amount
            }
        } else {
            to.withAccount {
                from.withAccount {
                    check(amount <= from.amount) { "Underflow" }
                    check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
                    from.amount -= amount
                }
            }
            to.amount += amount
        }

//      from.amount -= amount
//      to.amount += amount
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