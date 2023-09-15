package io.pleo.antaeus.core.services
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.EmailService
import kotlinx.coroutines.*
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.external.SlackIntegration
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import java.lang.Exception
import mu.KotlinLogging
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val emailService: EmailService,
    private val slackIntegration: SlackIntegration,
    private val config: BillingConfig
) {
    suspend fun autobill() {
        val currentDate = LocalDate.now()
        val statusesToProcessToday = config.getStatusesToProcessForDay(currentDate.dayOfWeek)

        // Bill pending invoices if it's the defined day of the month - currently at 1st of the month from the configuration
        if (config.shouldProcessPendingInvoices(currentDate)){
            billInvoices(InvoiceStatus.PENDING)
            }
        // If not the day of billing pending invoices then bill invoices based on schedule by day of the week - from the configuration
        else {
            for (status in statusesToProcessToday) {
                billInvoices(status)
            }
        }

        // Mark specific invoice statuses as permenent failed on a specific date - currently all failed statuses at the end of the month
        if (config.shouldMarkAsPermafail(currentDate)) {
            for (status in config.permanentFailStatuses )
            markInvoicesAsPermanentFailed(status)
        }
    }


    suspend fun billInvoices(invoiceStatus: InvoiceStatus){
        val invoicesToProcess = invoiceService.fetchByStatus(invoiceStatus)
        logger.info { "Start processing ${invoicesToProcess.size} invoices" }

        val scope = CoroutineScope(Dispatchers.Default)

        val jobs = invoicesToProcess.map { invoice ->
            scope.async {
                tryToChargeInvoice(invoice)
            }
        }

        jobs.awaitAll()

    }

    fun markInvoicesAsPermanentFailed(invoiceStatus: InvoiceStatus) {

        // Fetch invoices with the specified statuses
        val invoicesToMarkAsPermaFailed = invoiceService.fetchByStatus(invoiceStatus)

        // Update the statuses of the fetched invoices to PERMANENT_FAIL
        for (invoice in invoicesToMarkAsPermaFailed) {
            slackIntegration.sendMarkedPermanentFailMessage(invoice)
            invoiceService.updateStatus(invoice.id, InvoiceStatus.PERMANENT_FAIL)
        }
    }

    private suspend fun tryToChargeInvoice(invoice: Invoice){
        var retries = 0

        while (retries <= config.maxRetries) {
            try {
                val isInvoicePaid = paymentProvider.charge(invoice)

                if (isInvoicePaid){
                    //Invoice charged successfully
                    invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID)
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
            } catch (e: Exception){
                handleGenericException(invoice)
                break
            }
            retries++
        }

    }

    private fun handleCustomerInsufficientBalance(invoice: Invoice){
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_INSUFFICIENT_BALANCE)
        emailService.sendPaymentFailureEmail(invoice)
    }

    private fun handleCustomerNotFoundException(invoice: Invoice){
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_INVALID_CUSTOMER)
        slackIntegration.sendChargingFailureMessage(invoice, "Customer not Found")

    }

    private fun handleCurrencyMismatchException(invoice: Invoice){
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_INVALID_CURRENCY)
        slackIntegration.sendChargingFailureMessage(invoice, "Currency Mismatch")
    }

    private fun handleGenericException(invoice: Invoice){
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_UNKNOWN_ERROR)
        slackIntegration.sendChargingFailureMessage(invoice, "Unknown Error")

    }

    private suspend fun handleNetworkException(invoice: Invoice, retries: Int){
        logger.info { "Network error for Invoice ${invoice.id} during retry $retries)" } //todo: reword, maybe try instead of retry
        if (retries < config.maxRetries) {
            val delayMillis = config.delayFromRetryStrategy(retries)
            delay(delayMillis)
        } else {
            logger.info { "Max retries reached for ${invoice.id} and is now marked as failed" }
            invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_NETWORK_ERROR)
        }
    }

}