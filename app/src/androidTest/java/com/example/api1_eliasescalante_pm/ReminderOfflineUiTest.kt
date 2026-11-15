package com.example.api1_eliasescalante_pm

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flujo manual automatizado sobre la interfaz real de Compose:
 *
 *  1. Inicia sesión con una cuenta de prueba.
 *  2. Desconecta los datos del emulador.
 *  3. Crea un recordatorio desde la UI (Room primero).
 *  4. Comprueba que queda en estado "Pendiente".
 *  5. Reconecta, sincroniza y verifica que pasa a "Sincronizado" sin duplicarse.
 *  6. Elimina el recordatorio de prueba y cierra sesión.
 *
 * Las credenciales NO se guardan en el repositorio: se pasan como argumentos de
 * instrumentación. Si no se reciben, la prueba se omite.
 *
 *   .\gradlew.bat :app:connectedDebugAndroidTest `
 *     -Pandroid.testInstrumentationRunnerArguments.testEmail=... `
 *     -Pandroid.testInstrumentationRunnerArguments.testPassword=...
 *
 * Se usa `svc data disable/enable` porque en este emulador la red por defecto
 * es móvil (eth0); el modo avión no la desactiva.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ReminderOfflineUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val title = "prueba ui sin red"

    @After
    fun restoreNetworkAndSession() {
        // Nunca dejar el emulador sin red ni una sesión abierta tras un fallo.
        shell("svc data enable")
        shell("svc wifi enable")
        runCatching { runBlocking { AppContainer.authRepository.logout() } }
    }

    @Test
    fun offlineCreationStaysPendingAndSyncsOnceWhenNetworkReturns() {
        val arguments = InstrumentationRegistry.getArguments()
        val email = arguments.getString("testEmail")
        val password = arguments.getString("testPassword")
        assumeTrue(
            "Faltan testEmail/testPassword; se omite la prueba de interfaz.",
            !email.isNullOrBlank() && !password.isNullOrBlank()
        )

        runBlocking { AppContainer.authRepository.logout() }
        val login = runBlocking { AppContainer.authRepository.login(email!!, password!!) }
        assertTrue("El inicio de sesión falló: ${login.exceptionOrNull()}", login.isSuccess)

        // La lista solo se compone con sesión activa.
        waitForText("Sync", timeoutMillis = 60_000)

        // --- Sin red: la creación debe quedarse en Room como pendiente ---------
        shell("svc data disable")
        shell("svc wifi disable")
        Thread.sleep(3_000)

        composeRule.onNodeWithText("+").performClick()
        waitForText("Crear recordatorio")

        composeRule.onAllNodes(hasSetTextAction())[0].performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput(title)

        // 4 categorías (Trabajo, Personal, Salud, Otros) y luego 3 prioridades.
        composeRule.onAllNodes(isSelectable())[2].performClick() // Salud
        composeRule.onAllNodes(isSelectable())[4].performClick() // Alta

        composeRule.onNodeWithText("Guardar recordatorio")
            .performScrollTo()
            .performClick()

        waitForText(title, timeoutMillis = 30_000)
        // "Pendiente" es el estado de la tarea; "Pendiente de envío" el de sync.
        assertEquals("La tarjeta debe quedar pendiente de envío", 1, countExact("Pendiente de envío"))
        assertEquals("Todavía no debe estar sincronizada", 0, countExact("Sincronizado"))

        // --- Vuelve la red: sincronización sin duplicar ------------------------
        shell("svc data enable")
        shell("svc wifi enable")
        Thread.sleep(6_000)

        composeRule.onNodeWithText("Sync").performClick()
        waitForText("Sincronizado", timeoutMillis = 90_000)

        assertEquals("El recordatorio no debe duplicarse", 1, countExact(title))
        assertEquals("Ya no debe quedar nada pendiente de envío", 0, countExact("Pendiente de envío"))

        // --- Limpieza: eliminar el recordatorio de prueba ----------------------
        composeRule.onNodeWithText(title).performClick()
        waitForText("Detalle del recordatorio")

        composeRule.onNodeWithText("Eliminar").performClick()
        waitForText("Eliminar recordatorio")
        composeRule.onNode(hasText("Eliminar") and hasAnyAncestor(isDialog())).performClick()

        waitForText("Recordatorios", timeoutMillis = 30_000)
        waitForTextGone(title, timeoutMillis = 30_000)

        composeRule.onNodeWithText("Sync").performClick()
        Thread.sleep(4_000)
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun countExact(text: String): Int =
        composeRule.onAllNodesWithText(text, substring = false)
            .fetchSemanticsNodes()
            .size

    private fun waitForText(text: String, timeoutMillis: Long = 30_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text, substring = false)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForTextGone(text: String, timeoutMillis: Long = 30_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text, substring = false)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }
}
