package com.example.api1_eliasescalante_pm.data.mapper

import com.example.api1_eliasescalante_pm.data.local.ReminderEntity
import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderDto
import com.example.api1_eliasescalante_pm.domain.model.Reminder
import com.example.api1_eliasescalante_pm.domain.model.ReminderCategory
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderMappersTest {

    private val reminder = Reminder(
        id = "local-client-id",
        remoteId = 42L,
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

    @Test
    fun `entity to domain preserves every field`() {
        val entity = reminder.toEntity()
        val restored = entity.toDomain()

        assertEquals(reminder, restored)
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

    @Test
    fun `remote body roundtrip keeps description and metadata`() {
        val body = encodeRemoteBody(reminder)
        val metadata = parseRemoteMetadata(body)

        assertTrue(body.startsWith("Antes del día 10"))
        assertEquals("local-client-id", metadata["clientId"])
        assertEquals("PERSONAL", metadata["category"])
        assertEquals("HIGH", metadata["priority"])
        assertEquals("IN_PROGRESS", metadata["status"])
        assertEquals("1700000000000", metadata["dueDate"])
        assertEquals("Banco central", metadata["location"])
        assertEquals("200", metadata["updatedAt"])
        assertEquals("Antes del día 10", descriptionFromBody(body))
    }

    @Test
    fun `remote dto with clientId maps to domain using metadata fallback`() {
        val dto = RemoteReminderDto(
            remoteId = 7L,
            title = "Recordatorio remoto",
            body = encodeRemoteBody(reminder)
        )

        val domain = dto.toDomainOrNull()

        requireNotNull(domain)
        assertEquals("local-client-id", domain.id)
        assertEquals(7L, domain.remoteId)
        assertEquals(ReminderCategory.PERSONAL, domain.category)
        assertEquals(ReminderPriority.HIGH, domain.priority)
        assertEquals(SyncState.SYNCED, domain.syncState)
        assertEquals(1_700_000_000_000L, domain.dueDate)
    }

    @Test
    fun `foreign remote dto without clientId is ignored`() {
        val dto = RemoteReminderDto(
            remoteId = 1L,
            title = "sunt aut facere",
            body = "Post de ejemplo de JSONPlaceholder"
        )

        assertNull(dto.toDomainOrNull())
    }

    @Test
    fun `request includes stable clientId for reconciliation`() {
        val request = reminder.toRemoteRequest()

        assertEquals("local-client-id", request.clientId)
        assertEquals(reminder.title, request.title)
        assertEquals(reminder.updatedAt, request.updatedAt)
        assertTrue(request.body.contains("[recordatorio]"))
    }
}
