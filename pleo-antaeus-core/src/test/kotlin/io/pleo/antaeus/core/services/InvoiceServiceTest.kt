package io.pleo.antaeus.core.services

import io.mockk.*
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class InvoiceServiceTest {
    private val dal = mockk<AntaeusDal> {
        every { fetchInvoice(404) } returns null
    }

    private val invoiceService = InvoiceService(dal = dal)

    @Test
    fun `will throw if invoice is not found`() {
        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(404)
        }
    }

    @Test
    fun `will fetch if invoice exists`() {
        val invoiceMock = mockk<Invoice>()
        every { invoiceMock.id } returns 42
        every { dal.fetchInvoice(42) } returns invoiceMock

        assertDoesNotThrow {
            invoiceService.fetch(invoiceMock.id)
        }
    }

    @Test
    fun `update invoice status should throw InvoiceNotFoundException if the invoice does not exist`() {
        val invoiceId = 404
        every { dal.fetchInvoice(invoiceId) } returns null

        assertThrows<InvoiceNotFoundException> {
            invoiceService.updateStatus(invoiceId, InvoiceStatus.PAID)
        }
    }

    @Test
    fun `update invoice status should update Database if the invoice exists`() {
        val invoiceMock = mockk<Invoice>()

        every { dal.fetchInvoice(42) } returns invoiceMock
        every { dal.updateInvoiceStatus(42, InvoiceStatus.PAID) } just runs

        assertDoesNotThrow {
            invoiceService.updateStatus(42, InvoiceStatus.PAID)
        }

        verify {
            dal.updateInvoiceStatus(42, InvoiceStatus.PAID)
        }
    }
}
