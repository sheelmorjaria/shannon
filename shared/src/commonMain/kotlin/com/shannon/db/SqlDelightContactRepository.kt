package com.shannon.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.shannon.domain.model.Contact
import com.shannon.domain.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightContactRepository(
    private val database: ShannonDatabase
) : ContactRepository {

    override fun observeContacts(): Flow<List<Contact>> {
        return database.contactQueries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomainContact() } }
    }

    override suspend fun getContacts(): List<Contact> {
        return database.contactQueries
            .selectAll()
            .executeAsList()
            .map { it.toDomainContact() }
    }

    override suspend fun getContact(destinationHash: String): Contact? {
        return database.contactQueries
            .selectByHash(destinationHash)
            .executeAsOneOrNull()
            ?.toDomainContact()
    }

    override suspend fun saveContact(contact: Contact) {
        database.contactQueries.insert(
            destination_hash = contact.destinationHash,
            display_name = contact.displayName,
            public_key = contact.publicKey
        )
    }

    override suspend fun deleteContact(destinationHash: String) {
        database.contactQueries.deleteByHash(destinationHash)
    }
}

private fun com.shannon.db.Contact.toDomainContact(): Contact {
    return Contact(
        destinationHash = destination_hash,
        displayName = display_name,
        publicKey = public_key
    )
}
