/*
 * The `BillingService` class is responsible for automating the billing process for invoices.
 * It handles various billing-related operations, such as charging invoices, updating invoice statuses,
 * sending notifications, and handling exceptions.
 *
 * Class Parameters:
 *
 * paymentProvider: The payment provider responsible for processing invoice charges.
 * invoiceService: The service for managing invoice data and statuses.
 * emailService: The service for sending email notifications to customers related to billing.
 * slackIntegration: The integration with Slack for sending notifications about errors that should be addressed internally.
 * billingConfiguration: Configuration settings for billing, including billing schedules and retry strategies.
 *
 * Functions:
 *
 * - `autoBilling()`: Initiates the automated billing process, including billing pending invoices on the defined
 *   day of the month (first day of the month) and retrying failed invoices based on a predefined schedule by day of the week.
 *   It also marks failed invoice statuses as permanent failures on certain date (last day of the month).
 *
 * - `billInvoices(invoiceStatus: InvoiceStatus)`: Attempts to bill invoices with the specified status. It processes
 *   invoices concurrently using coroutines.
 *
 * - `markInvoicesAsPermanentFailed(invoiceStatus: InvoiceStatus)`: Marks invoices with the specified status as
 *   permanent failures (essentially removing them from the automated billing process).
 *
 * - `tryToChargeInvoice(invoice: Invoice)`: Tries to bill a given invoice. It handles various exceptions and
 *   retries billing on the spot in case of network errors.
 *
 */
package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceAlreadyChargedException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.EmailService
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.external.SlackIntegration
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import java.lang.Exception
import java.time.LocalDate
import mu.KotlinLogging


private val logger = KotlinLogging.logger {}

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val emailService: EmailService,
    private val slackIntegration: SlackIntegration,
    private val billingConfiguration: BillingConfiguration
) {
    /**
     * Initiates the automated billing process. It determines the current date, fetches the statuses that
     * need to be billed on this day, and processes billing accordingly.
     *
     * - If today is the defined day of the month for billing pending invoices (currently the 1st of the month),
     *   it bills pending invoices with status 'PENDING.'
     *
     * - If today is not the day for billing pending invoices, it retries billing of invoices previously marked as failed
     *   based on the configured schedule by the day of the week.
     *
     * - Additionally, it checks if specific invoice statuses should be marked as permanent failures based on
     *   the current date. Currently, all failed statuses are marked as permanent failures at the end of the month.
     *
     */
    suspend fun autoBilling() {
        logger.info { "Autobilling started"}
        val currentDate = LocalDate.now()
        val statusesToBill = billingConfiguration.getStatusesToBill(currentDate.dayOfWeek)

        // Bill pending invoices if it's the defined day of the month - currently at 1st of the month from the configuration
        if (billingConfiguration.shouldBillPendingInvoices(currentDate)){
            logger.info { "Monthly billing of pending invoices initiated"}
            billInvoices(InvoiceStatus.PENDING)
            }
        // If not the day of billing pending invoices then bill invoices based on schedule by day of the week - from the configuration
        else {
            logger.info { "Auto-billing initiated on $currentDate, will retry: ${statusesToBill.joinToString()}" }
            for (status in statusesToBill) {
                billInvoices(status)
            }
        }

        // Mark specific invoice statuses as permanent failed on a specific date - currently all failed statuses at the end of the month
        if (billingConfiguration.shouldMarkAsPermanentFailed(currentDate)) {
            logger.info { "Cleanup of invoices that have been failed to paid throughout this month"}
            for (status in billingConfiguration.permanentFailStatuses )
            markInvoicesAsPermanentFailed(status)
        }
    }


    //  Attempts to bill invoices with the specified status concurrently.
    suspend fun billInvoices(invoiceStatus: InvoiceStatus){
        val invoicesToProcess = invoiceService.fetchByStatus(invoiceStatus)
        logger.info { "Start processing ${invoicesToProcess.size} $invoiceStatus invoices" }

        val scope = CoroutineScope(Dispatchers.Default)

        val jobs = invoicesToProcess.map { invoice ->
            scope.async {
                tryToChargeInvoice(invoice)
            }
        }

        jobs.awaitAll()

    }

/**
 Marks invoices with the specified status as permanent failures and updates their statuses accordingly.
 This function is called to handle cases where invoices cannot be successfully processed after multiple attempts.
 It notifies internal channel through Slack Integration about the permanent failure and updates the invoice status to 'PERMANENT_FAIL.'
 */
    fun markInvoicesAsPermanentFailed(invoiceStatus: InvoiceStatus) {

        // Fetch invoices with the specified statuses
        val invoicesToMarkAsPermaFailed = invoiceService.fetchByStatus(invoiceStatus)

        // Update the statuses of the fetched invoices to PERMANENT_FAIL
        for (invoice in invoicesToMarkAsPermaFailed) {
            slackIntegration.sendMarkedPermanentFailMessage(invoice)
            invoiceService.updateStatus(invoice.id, InvoiceStatus.PERMANENT_FAIL)
        }
        logger.info {"${invoicesToMarkAsPermaFailed.size} $invoiceStatus Invoices marked as permanent failed"}
    }

    //Tries to charge the given invoice, handling various exceptions and retrying if necessary.
    suspend fun tryToChargeInvoice(invoice: Invoice){
        var retries = 0

        while (retries <= billingConfiguration.maxRetries) {
            try {
                val isInvoicePaid = paymentProvider.charge(invoice)

                if (isInvoicePaid){
                    //Invoice charged successfully
                    invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID)
                    emailService.sendBillingSuccessEmail(invoice) //notify customer about successful billing
                    break
                }
                else {
                    //Invoice charged failed because customer account balance did not allow the charge
                    handleCustomerInsufficientBalance(invoice)
                    break
                }
            } catch (e: NetworkException){
                //Network Error Occurred, retry with an exponential backoff strategy
                handleNetworkException(invoice, retries)
                //Other exceptions
            } catch (e:CustomerNotFoundException){
                handleCustomerNotFoundException(invoice)
                break
            } catch (e:CurrencyMismatchException){
                handleCurrencyMismatchException(invoice)
                break
            } catch (e:InvoiceAlreadyChargedException){
                handleInvoiceAlreadyChargedException(invoice)
                break
            } catch (e: Exception){
                handleGenericException(invoice)
                break
            }
            retries++
        }

    }

    // In case of Insufficient Balance, inform customer by email and update invoice status accordingly
    private fun handleCustomerInsufficientBalance(invoice: Invoice){
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_INSUFFICIENT_BALANCE)
        emailService.sendBillingFailureEmail(invoice)
    }

    // In case of customer not found error, inform internal channel and update invoice status accordingly
    private fun handleCustomerNotFoundException(invoice: Invoice){
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_INVALID_CUSTOMER)
        slackIntegration.sendChargingFailureMessage(invoice, "Customer not Found")

    }

    // In case of currency mismatch, inform internal channel and update invoice status accordingly
    private fun handleCurrencyMismatchException(invoice: Invoice){
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_INVALID_CURRENCY)
        slackIntegration.sendChargingFailureMessage(invoice, "Currency Mismatch")
    }

    //In case of trying to charge an already successfully charged invoice log the event
    private fun handleInvoiceAlreadyChargedException(invoice:Invoice){
        logger.info { " ${invoice.id} has already been charged" }

    }


    // In case of unknown error, inform internal channel and update invoice status accordingly
    private fun handleGenericException(invoice: Invoice){
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_UNKNOWN_ERROR)
        slackIntegration.sendChargingFailureMessage(invoice, "Unknown Error")

    }

    // In case of network error, retry on the spot based on retry strategy from configuration
    private suspend fun handleNetworkException(invoice: Invoice, retries: Int){
        logger.info { "Network error for Invoice ${invoice.id} during retry $retries)" }
        if (retries < billingConfiguration.maxRetries) {
            val delayMillis = billingConfiguration.delayFromRetryStrategy(retries)
            delay(delayMillis)
        } else {
            logger.info { "Max retries reached for ${invoice.id} and is now marked as failed" }
            invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_NETWORK_ERROR)
        }
    }

}