package com.example.api1_eliasescalante_pm.data.mapper

import com.example.api1_eliasescalante_pm.data.cloud.CloudReminder
import com.example.api1_eliasescalante_pm.data.cloud.toCloudReminderOrNull
import com.example.api1_eliasescalante_pm.data.cloud.toDocument
import com.example.api1_eliasescalante_pm.data.local.ReminderEntity
import com.example.api1_eliasescalante_pm.domain.model.Reminder
import com.example.api1_eliasescalante_pm.domain.model.ReminderCategory
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de los mapeos Room <-> Dominio y Dominio <-> Firestore.
 * No requieren Android ni credenciales de Firebase.
 */
class ReminderMappersTest {

    private val reminder = Reminder(
        id = "550e8400-e29b-41d4-a716-446655440000",
        ownerUid = "uid-cuenta-a",
        title = "Pagar el alquiler",
        description = "Antes del día 10",
        category = ReminderCategory.PERSONAL,
        priority = ReminderPriority.HIGH,
        status = ReminderStatus.IN_PROGRESS,
        dueDate = 1_700_000_000_000L,
        locationOrNotice = "Banco central",
        syncState = SyncState.SYNCED,
        syncError = null,
        createdAt = 100L,
        updatedAt = 200L
    )

    // -----------------------------------------------------------------------
    // Room <-> Dominio
    // -----------------------------------------------------------------------

    @Test
    fun `entity to domain preserves every field`() {
        val entity = reminder.toEntity()
        val restored = entity.toDomain()

        assertEquals(reminder, restored)
        assertEquals("uid-cuenta-a", entity.ownerUid)
    }

    @Test
    fun `domain to entity stores enums as storage strings`() {
        val entity = reminder.toEntity()

        assertEquals("PERSONAL", entity.category)
        assertEquals("HIGH", entity.priority)
        assertEquals("IN_PROGRESS", entity.status)
        assertEquals("SYNCED", entity.syncState)
    }

    @Test
    fun `unknown stored values fall back to safe defaults`() {
        val entity = ReminderEntity(
            id = "id",
            ownerUid = "uid",
            title = "Título",
            category = "NO_EXISTE",
            priority = "NO_EXISTE",
            status = "NO_EXISTE",
            syncState = "NO_EXISTE",
            createdAt = 1L,
            updatedAt = 1L
        )

        val domain = entity.toDomain()

        assertEquals(ReminderCategory.OTHER, domain.category)
        assertEquals(ReminderPriority.MEDIUM, domain.priority)
        assertEquals(ReminderStatus.PENDING, domain.status)
        assertEquals(SyncState.PENDING, domain.syncState)
    }

    // -----------------------------------------------------------------------
    // Dominio -> Firestore
    // -----------------------------------------------------------------------

    @Test
    fun `document keeps stable id and ownerUid for the security rules`() {
        val document = reminder.toCloud().toDocument()

        assertEquals(reminder.id, document["id"])
        assertEquals(reminder.ownerUid, document["ownerUid"])
        assertEquals("PERSONAL", document["category"])
        assertEquals("HIGH", document["priority"])
        assertEquals("IN_PROGRESS", document["status"])
        assertEquals(1_700_000_000_000L, document["dueDate"])
        assertEquals(200L, document["updatedAt"])
        assertEquals(CloudReminder.CURRENT_SCHEMA_VERSION, document["schemaVersion"])
    }

    @Test
    fun `cloud document roundtrip returns a synced domain reminder`() {
        val document = reminder.toCloud().toDocument()

        val parsed = document.toCloudReminderOrNull(
            documentId = reminder.id,
            expectedOwnerUid = reminder.ownerUid
        )
        requireNotNull(parsed)

        val domain = parsed.toDomain()
        assertEquals(reminder.id, domain.id)
        assertEquals(reminder.ownerUid, domain.ownerUid)
        assertEquals(reminder.title, domain.title)
        assertEquals(reminder.description, domain.description)
        assertEquals(reminder.category, domain.category)
        assertEquals(reminder.priority, domain.priority)
        assertEquals(reminder.status, domain.status)
        assertEquals(reminder.dueDate, domain.dueDate)
        assertEquals(reminder.locationOrNotice, domain.locationOrNotice)
        assertEquals(SyncState.SYNCED, domain.syncState)
        assertNull(domain.syncError)
    }

    @Test
    fun `document owned by another account is rejected`() {
        val document = reminder.toCloud().toDocument()

        val parsed = document.toCloudReminderOrNull(
            documentId = reminder.id,
            expectedOwnerUid = "uid-cuenta-b"
        )

        assertNull(parsed)
    }

    @Test
    fun `document whose id does not match its path is rejected`() {
        val document = reminder.toCloud().toDocument()

        val parsed = document.toCloudReminderOrNull(
            documentId = "otro-id",
            expectedOwnerUid = reminder.ownerUid
        )

        assertNull(parsed)
    }

    @Test
    fun `document without a valid title is rejected`() {
        val document = reminder.toCloud().toDocument() + ("title" to "   ")

        val parsed = document.toCloudReminderOrNull(
            documentId = reminder.id,
            expectedOwnerUid = reminder.ownerUid
        )

        assertNull(parsed)
    }

    @Test
    fun `document with numeric timestamps survives Firestore number types`() {
        val document = mapOf<String, Any?>(
            "id" to "doc-1",
            "ownerUid" to "uid-a",
            "title" to "Recordatorio",
            "category" to "WORK",
            "priority" to "LOW",
            "status" to "COMPLETED",
            "createdAt" to 1_000, // Int, como puede devolverlo Firestore
            "updatedAt" to 2_000L
        )

        val parsed = document.toCloudReminderOrNull("doc-1", "uid-a")
        requireNotNull(parsed)

        assertEquals(1_000L, parsed.createdAt)
        assertEquals(2_000L, parsed.updatedAt)
        assertEquals(ReminderCategory.WORK, parsed.category)
        assertEquals(ReminderStatus.COMPLETED, parsed.status)
    }

    @Test
    fun `sync state survives the entity roundtrip`() {
        val pending = reminder.copy(syncState = SyncState.PENDING, syncError = "sin red")

        val restored = pending.toEntity(isDeleted = true, syncedAt = 300L).toDomain()

        assertEquals(SyncState.PENDING, restored.syncState)
        assertEquals("sin red", restored.syncError)
        assertTrue(pending.toEntity(isDeleted = true).isDeleted)
    }
}
