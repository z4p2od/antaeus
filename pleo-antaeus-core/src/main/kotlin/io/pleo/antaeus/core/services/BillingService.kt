package io.pleo.antaeus.core.services
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.NetworkException
import kotlinx.coroutines.*
import kotlin.math.pow
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import java.lang.Exception
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val maxRetries: Int = 4 // Default Number of Max retries


) {
    suspend fun billInvoices() {
        /**
         * Attempt to bill pending invoices asynchronously with retries and exponential backoff in the case of Network Error .
         * This function fetches pending invoices, attempts to charge them, and updates their status.
         */
        val pendingInvoices = invoiceService.fetchByStatus(InvoiceStatus.PENDING)
        logger.info { "Start processing ${pendingInvoices.size} invoices" }
        val jobs = pendingInvoices.map { invoice ->
            CoroutineScope(Dispatchers.Default).async {//Todo maybe create a single coroutine scope for the whole function
                var retries = 0

                retry@ while (retries <= maxRetries) {
                    try {
                        val isInvoicePaid = paymentProvider.charge(invoice) //Todo: maybe change dispatchers to I/O here

                        if (isInvoicePaid) {
                            // Invoice is paid, update its status and exit retry loop
                            invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID)
                            break@retry
                        }
                    } catch (e: Exception) {
                        when (e) {
                            //Handle network exeption with retries on the spot
                            is NetworkException -> {
                                logger.info { "Network error for Invoice ${invoice.id} this was the  $retries retry" }
                                if (retries < maxRetries) {
                                    // Perform exponential backoff before the next retry TODO Refactor in a separate function maybe
                                    val delayMillis = 2.0.pow(retries.toDouble()).toLong() * 1000
                                    delay(delayMillis) //todo: dispatchers I/O maybe
                                } else {
                                    // Max retries reached, update invoice status to failed
                                    logger.info { "Max retries reached for ${invoice.id} and is now marked as failed" }
                                    invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED)
                                }
                            }
                            else -> {
                                // For all other exceptions, update invoice status to generic failure
                                invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED)
                                break@retry
                            }
                        }
                    }
                    retries++
                }
            }
        }
        jobs.awaitAll()
    }
}