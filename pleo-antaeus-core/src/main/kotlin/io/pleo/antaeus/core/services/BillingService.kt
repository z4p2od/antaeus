package io.pleo.antaeus.core.services
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
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

    private suspend fun tryToChargeInvoice(invoice: Invoice){
        var retries = 0

        while (retries <= maxRetries) {
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
        logger.info { "Not enough balance to charge Invoice ID: ${invoice.id}  of Customer ID: ${invoice.customerId }"}
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_INSUFFICIENT_BALANCE)
        //TODO: inform customer that payment failed due to low balance
    }

    private fun handleCustomerNotFoundException(invoice: Invoice){
        logger.info { "Customer ID: ${invoice.customerId } not found for Invoice ID: ${invoice.id} "}
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_INVALID_CUSTOMER)
        //TODO: inform support to handle it

    }

    private fun handleCurrencyMismatchException(invoice: Invoice){
        logger.info {"Currency mismatch for Invoice Id: ${invoice.id}" } //:todo maybe infer currency
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_INVALID_CURRENCY)
        //TODO: inform support to handle it
    }

    private fun handleGenericException(invoice: Invoice){
        logger.info {"Unknown error while attempting to charge Invoice ID ${invoice.id}"}
        invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_UNKNOWN_ERROR)
        //TODO: inform support to handle it
    }

    private suspend fun handleNetworkException(invoice: Invoice, retries: Int){
        logger.info { "Network error for Invoice ${invoice.id} during retry $retries)" } //todo: reword, maybe try instead of retry
        if (retries < maxRetries) {
            val delayMillis = calculateExponentialBackoff(retries)
            delay(delayMillis)
        } else {
            logger.info { "Max retries reached for ${invoice.id} and is now marked as failed" }
            invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED_NETWORK_ERROR)
        }
    }

    private fun calculateExponentialBackoff(retries: Int): Long {
        return (2.0.pow(retries.toDouble()) * 1000).toLong()
    }
}