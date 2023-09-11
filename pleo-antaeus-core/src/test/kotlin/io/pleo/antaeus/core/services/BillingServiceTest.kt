package io.pleo.antaeus.core.services

import io.mockk.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.InvoiceStatus
import org.junit.jupiter.api.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.* // Import the TestCoroutineDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class BillingServiceTest {
    //mock dependencies
    private val paymentProvider = mockk<PaymentProvider>()
    private val invoiceService = mockk<InvoiceService>(relaxed = true)

    // Create a TestCoroutineDispatcher
    private val testDispatcher = TestCoroutineDispatcher()

    //create billing service instant
    private val billingService = BillingService(
        paymentProvider = paymentProvider,
        invoiceService = invoiceService
    )

    @BeforeEach
    fun setUp() {
        // Set the TestCoroutineDispatcher as the main dispatcher
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        // Reset the main dispatcher after the test
        Dispatchers.resetMain()
        // Cleanup the TestCoroutineDispatcher
        testDispatcher.cleanupTestCoroutines()
    }
    @Test
     fun `should not process any invoice`() =testDispatcher.runBlockingTest  {
        //Arrange
        every { invoiceService.fetchByStatus(any()) } returns emptyList()

        //Act
        billingService.billInvoices(invoiceStatus = InvoiceStatus.PENDING)
        //Assert
        verify(exactly = 0) { paymentProvider.charge(any()) }
        verify(exactly = 0) { invoiceService.updateStatus(any(), any()) }
    }
}



