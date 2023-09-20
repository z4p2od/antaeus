package io.pleo.antaeus.core.services

import io.mockk.*
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.external.EmailService
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.external.SlackIntegration
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek

const val INVOICE_ID = 42
const val CUSTOMER_ID = 44

class BillingServiceTest {
    //mock dependencies
    private val paymentProvider = mockk<PaymentProvider>()
    private val invoiceService = mockk<InvoiceService>(relaxed = true)
    private val emailService = mockk<EmailService>(relaxed = true)
    private val slackIntegration = mockk<SlackIntegration>(relaxed = true)
    private val billingConfiguration = mockk<BillingConfiguration>(relaxed = true)


    //create billing service instant
    private val billingService = BillingService(
        paymentProvider = paymentProvider,
        invoiceService = invoiceService,
        emailService = emailService,
        slackIntegration = slackIntegration,
        billingConfiguration = billingConfiguration
    )

    @Test
     fun `should not process any invoice`() = runTest{

        every { invoiceService.fetchByStatus(any()) } returns emptyList()

        billingService.billInvoices(invoiceStatus = InvoiceStatus.PENDING)

        verify(exactly = 0) { paymentProvider.charge(any()) }
        verify(exactly = 0) { invoiceService.updateStatus(any(), any()) }
    }

    @Test
     fun `should bill once invoice and update statuses`() = runTest{

        val invoice = mockk<Invoice>() {
            every { id } returns INVOICE_ID
        }
        every { invoiceService.fetchByStatus(InvoiceStatus.PENDING) } returns listOf(invoice)
        every { paymentProvider.charge(invoice) } returns true
        every { invoiceService.updateStatus(any(), any()) } just runs

        billingService.billInvoices(invoiceStatus = InvoiceStatus.PENDING)

        verify {
            paymentProvider.charge(invoice)
            invoiceService.updateStatus(INVOICE_ID, InvoiceStatus.PAID)
        }
    }

    @Test
     fun `should fail when payment povider doesn't charge`() = runTest{
        val invoice = mockk<Invoice>() {
            every { id } returns INVOICE_ID
        }
        every { invoiceService.fetchByStatus(InvoiceStatus.PENDING) } returns listOf(invoice)
        every { paymentProvider.charge(invoice) } returns false

        billingService.billInvoices(invoiceStatus = InvoiceStatus.PENDING)

        verify {
            paymentProvider.charge(invoice)
            invoiceService.updateStatus(INVOICE_ID, InvoiceStatus.FAILED_INSUFFICIENT_BALANCE)
            emailService.sendBillingFailureEmail(invoice)
        }

    }

    @Test
     fun `should set state to Invalid Currency when there's a currency missmatch`() = runTest{
        val invoice = mockk<Invoice>() {
            every { id } returns INVOICE_ID
        }
        val customer = mockk<Customer>(){
            every {id} returns CUSTOMER_ID
        }

        every { invoiceService.fetchByStatus(InvoiceStatus.PENDING) } returns listOf(invoice)
        every { paymentProvider.charge(invoice) } throws CurrencyMismatchException(invoice.id, customer.id)

        billingService.billInvoices(invoiceStatus = InvoiceStatus.PENDING)

        verify {
            paymentProvider.charge(invoice)
            invoiceService.updateStatus(INVOICE_ID, InvoiceStatus.FAILED_INVALID_CURRENCY)
            slackIntegration.sendChargingFailureMessage(invoice,any())
        }

    }
    @Test
     fun `should bill pending invoices at specific date`() = runTest{
        // Everything inside this block runs in a coroutine scope

        every { billingConfiguration.shouldBillPendingInvoices(any()) } returns true

        billingService.autoBilling()

        coVerify {
            billingService.billInvoices(invoiceStatus = InvoiceStatus.PENDING)
        }
    }

}



