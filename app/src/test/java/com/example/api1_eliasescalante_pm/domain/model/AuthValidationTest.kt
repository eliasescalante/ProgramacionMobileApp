package com.example.api1_eliasescalante_pm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Las validaciones se ejecutan antes de llamar a Firebase. No reemplazan las
 * reglas del servidor: solo evitan viajes innecesarios y mensajes crípticos.
 */
class AuthValidationTest {

    @Test
    fun `valid email has no error`() {
        assertNull(AuthValidation.emailError("persona@ejemplo.com"))
        assertNull(AuthValidation.emailError("  persona@ejemplo.com  "))
    }

    @Test
    fun `empty or malformed email is rejected`() {
        assertEquals("Ingresá tu correo electrónico.", AuthValidation.emailError(""))
        assertEquals(
            "El correo no tiene un formato válido.",
            AuthValidation.emailError("persona")
        )
        assertEquals(
            "El correo no tiene un formato válido.",
            AuthValidation.emailError("persona@ejemplo")
        )
    }

    @Test
    fun `password must have the minimum length`() {
        assertEquals(
            "La contraseña debe tener al menos 6 caracteres.",
            AuthValidation.passwordError("12345")
        )
        assertNull(AuthValidation.passwordError("123456"))
    }

    @Test
    fun `empty password has its own message`() {
        assertEquals("Ingresá tu contraseña.", AuthValidation.passwordError(""))
    }

    @Test
    fun `confirmation must match`() {
        assertNull(AuthValidation.confirmationError("secreto", "secreto"))
        assertEquals(
            "Las contraseñas no coinciden.",
            AuthValidation.confirmationError("secreto", "otro")
        )
        assertEquals(
            "Repetí la contraseña.",
            AuthValidation.confirmationError("secreto", "")
        )
    }

    @Test
    fun `auth mode toggles between login and register`() {
        assertEquals(AuthMode.REGISTER, AuthMode.LOGIN.toggle())
        assertEquals(AuthMode.LOGIN, AuthMode.REGISTER.toggle())
    }

    @Test
    fun `short uid never exposes more than eight characters`() {
        assertEquals("12345678…", AuthUser("1234567890abcdef", null).shortUid)
        assertEquals("abc", AuthUser("abc", null).shortUid)
    }
}
