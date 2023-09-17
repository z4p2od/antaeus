package io.pleo.antaeus.core.exceptions

class InvoiceAlreadyChargedException(id: Int) : Exception("Invoice $id has already been charged")
