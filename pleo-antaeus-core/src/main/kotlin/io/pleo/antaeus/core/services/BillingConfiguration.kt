/*
 * This file defines the BillingConfiguration class, responsible for configuring the scheduling
 * and retry logic of the billing service. It includes settings for maximum retry counts, defines
 * which invoice statuses should be marked as permanent failures and when, sets billing schedules, and
 * calculates retry delay times.
 *
 * maxRetries: Specifies the maximum number of retries for network and unknown errors.
 * permanentFailStatuses: Lists the invoice statuses to mark as permanent failures.
 * statusesToBill: Maps each day of the week to the statuses to process on that day.
 * getStatusesToBill: Determines which invoices should be billed on a specific day of the week.
 * shouldBillPendingInvoices: Checks if pending invoices should be billed - Currently true on the first day of the month.
 * shouldMarkAsPermanentFailed: Checks if invoices should be marked as permanent failures - Currently true on the last day of the month.
 * delayFromRetryStrategy: Calculates the delay before the next retry based on a retry strategy.
 *  */


package io.pleo.antaeus.core.services

import io.pleo.antaeus.models.InvoiceStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.pow


class BillingConfiguration {
    // Define maximum number of retries for Network & Unknown Errors
    val maxRetries: Int = 4

    // Define invoice statuses to mark as permanent-fail
    val permanentFailStatuses: List<InvoiceStatus> = listOf(
        InvoiceStatus.FAILED_INSUFFICIENT_BALANCE,
        InvoiceStatus.FAILED_INVALID_CUSTOMER,
        InvoiceStatus.FAILED_INVALID_CURRENCY,
        InvoiceStatus.FAILED_NETWORK_ERROR,
        InvoiceStatus.FAILED_UNKNOWN_ERROR
    )

    // Define a map that maps each day of the week to the statuses to process on that day.
    private val statusesToBill: Map<DayOfWeek, List<InvoiceStatus>> = mapOf(
        DayOfWeek.MONDAY to listOf(InvoiceStatus.FAILED_NETWORK_ERROR),
        DayOfWeek.TUESDAY to listOf(InvoiceStatus.FAILED_NETWORK_ERROR),
        DayOfWeek.WEDNESDAY to listOf(
            InvoiceStatus.FAILED_NETWORK_ERROR,
            InvoiceStatus.FAILED_INVALID_CURRENCY,
            InvoiceStatus.FAILED_INVALID_CUSTOMER,
            InvoiceStatus.FAILED_UNKNOWN_ERROR
        ),
        DayOfWeek.THURSDAY to listOf(InvoiceStatus.FAILED_NETWORK_ERROR),
        DayOfWeek.FRIDAY to listOf(
            InvoiceStatus.FAILED_NETWORK_ERROR, InvoiceStatus.FAILED_INSUFFICIENT_BALANCE
        ),
        DayOfWeek.SATURDAY to listOf(InvoiceStatus.FAILED_NETWORK_ERROR),
        DayOfWeek.SUNDAY to listOf(InvoiceStatus.FAILED_NETWORK_ERROR, InvoiceStatus.PENDING)
    )

    // Function to determine which invoices should be charged a given day of the week
    fun getStatusesToBill(dayOfWeek: DayOfWeek): List<InvoiceStatus> {
        return statusesToBill[dayOfWeek] ?: emptyList()
    }

    // Function to determine if pending invoices should be charged at a given day (currently first day of the month)
    fun shouldBillPendingInvoices(currentDate: LocalDate): Boolean {
        return isFirstDayOfMonth(currentDate)
    }

    // Function to determine if invoices should be marked as permanentFailed at a given day (currently last day of the month)
    fun shouldMarkAsPermanentFailed(currentDate: LocalDate): Boolean {
        return isLastDayOfMonth(currentDate)
    }

    // Function that returns the delay before the next try based on a given retry strategy
    fun delayFromRetryStrategy(retries: Int): Long {
        return exponentialBackoff(retries)
    }

    // Function to check if it's the first day of the month
    private fun isFirstDayOfMonth(currentDate: LocalDate): Boolean {
        return currentDate.dayOfMonth == 1
    }

    // Function to check if it's the last day of the month
    private fun isLastDayOfMonth(currentDate: LocalDate): Boolean {
        return currentDate == currentDate.with(TemporalAdjusters.lastDayOfMonth())
    }

    //function to implement exponential backoff strategy
    private fun exponentialBackoff(retries: Int): Long {
        val exponentialBackoffBase: Double = 2.0 // Define the base here
        val baseDelayMillis: Long = 1000 // 1 second
        val maxDelayMillis: Long = 60000 // 60 seconds

        // exponential backoff logic: delay=base^retries * milliseconds
        val delayMillis = (exponentialBackoffBase.pow(retries.toDouble()) * baseDelayMillis).toLong()

        // Ensure the delay does not exceed the maximum
        return minOf(delayMillis, maxDelayMillis)
    }
}
