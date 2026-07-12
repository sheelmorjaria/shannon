package com.shannon.domain.repository

import com.shannon.domain.model.Contact
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for contact/address book management.
 */
interface ContactRepository {
    /** Observe all contacts. */
    fun observeContacts(): Flow<List<Contact>>

    /** Get all contacts. */
    suspend fun getContacts(): List<Contact>

    /** Get a contact by destination hash. */
    suspend fun getContact(destinationHash: String): Contact?

    /** Save a new contact. */
    suspend fun saveContact(contact: Contact)

    /** Delete a contact by destination hash. */
    suspend fun deleteContact(destinationHash: String)
}
