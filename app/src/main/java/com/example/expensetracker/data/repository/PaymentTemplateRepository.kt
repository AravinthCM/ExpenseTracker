package com.example.expensetracker.data.repository

import com.example.expensetracker.data.local.PaymentTemplateDao
import com.example.expensetracker.data.local.PaymentTemplateEntity
import kotlinx.coroutines.flow.Flow

class PaymentTemplateRepository(private val dao: PaymentTemplateDao) {

    val allTemplates: Flow<List<PaymentTemplateEntity>> = dao.getAllTemplatesFlow()

    fun getAllTemplatesSync(): List<PaymentTemplateEntity> {
        return dao.getAllTemplatesSync()
    }

    fun getEnabledTemplatesSync(): List<PaymentTemplateEntity> {
        return dao.getEnabledTemplatesSync()
    }

    fun insert(template: PaymentTemplateEntity) {
        dao.insert(template)
    }

    fun update(template: PaymentTemplateEntity) {
        dao.update(template)
    }

    fun delete(template: PaymentTemplateEntity) {
        dao.delete(template)
    }

    fun deleteById(id: String) {
        dao.deleteById(id)
    }
}
