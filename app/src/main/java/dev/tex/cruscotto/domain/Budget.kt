package dev.tex.cruscotto.domain

import dev.tex.cruscotto.data.Category
import dev.tex.cruscotto.data.Kind
import dev.tex.cruscotto.data.Txn
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class CategoryRow(
    val category: Category,
    val budgetCents: Long,
    val spentCents: Long,
    /** Effective budget after rebalancing: grows for categories that overspent, shrinks for buffers used. */
    val effectiveCents: Long,
    val overCents: Long,
    val takenCents: Long,
    val effectivePct: Double,
) {
    val remainingCents: Long get() = max(0, effectiveCents - spentCents)
}

data class BudgetMonth(
    val month: YearMonth,
    val baseCents: Long,
    val incomeCents: Long,
    val usesSalary: Boolean,
    val outCents: Long,
    val rows: List<CategoryRow>,
    val totalOverCents: Long,
    val deficitCents: Long,
    val orphanCents: Long,
    val txns: List<Txn>,
)

fun YearMonth.contains(epochDay: Long): Boolean = YearMonth.from(LocalDate.ofEpochDay(epochDay)) == this

/**
 * Monthly budget. Each expense category gets base × share%.
 * The total overspend (categories over budget + expenses without a category) is taken from the buffer
 * categories in their order, never below what a buffer has already spent. Whatever is left is the deficit.
 */
fun computeMonth(month: YearMonth, allTxns: List<Txn>, categories: List<Category>, salaryCents: Long): BudgetMonth {
    val txns = allTxns.filter { month.contains(it.epochDay) }
    val income = txns.filter { it.type == Kind.IN }.sumOf { it.amountCents }
    val base = if (income > 0) income else salaryCents
    val outs = txns.filter { it.type == Kind.OUT }
    val outCats = categories.filter { it.kind == Kind.OUT }.sortedBy { it.sort }
    val known = outCats.map { it.id }.toSet()
    val orphan = outs.filter { it.categoryId !in known }.sumOf { it.amountCents }

    data class Work(val cat: Category, val budget: Long, val spent: Long, var eff: Long, var taken: Long = 0)
    val work = outCats.map { c ->
        val budget = (base * c.pct / 100.0).roundToLong()
        val spent = outs.filter { it.categoryId == c.id }.sumOf { it.amountCents }
        Work(c, budget, spent, max(budget, spent))
    }
    val totalOver = work.sumOf { max(0, it.spent - it.budget) } + orphan
    var remaining = totalOver
    for (w in work.filter { it.cat.isBuffer }.sortedBy { it.cat.bufferOrder }) {
        if (remaining <= 0) break
        val headroom = max(0, w.eff - w.spent)
        val take = min(headroom, remaining)
        w.eff -= take
        w.taken = take
        remaining -= take
    }
    val rows = work.map {
        CategoryRow(
            category = it.cat, budgetCents = it.budget, spentCents = it.spent, effectiveCents = it.eff,
            overCents = max(0, it.spent - it.budget), takenCents = it.taken,
            effectivePct = if (base > 0) it.eff * 100.0 / base else 0.0,
        )
    }
    return BudgetMonth(month, base, income, income <= 0, outs.sumOf { it.amountCents }, rows, totalOver, remaining, orphan, txns)
}
