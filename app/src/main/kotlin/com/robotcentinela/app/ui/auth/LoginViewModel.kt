package com.robotcentinela.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robotcentinela.app.identity.application.RecoverAccess
import com.robotcentinela.app.identity.application.SignIn
import com.robotcentinela.app.shell.DevAccount
import com.robotcentinela.app.ui.app.UiPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado observable de la pantalla de inicio de sesión (HU01, RF01).
 */
data class LoginUiState(
    val email: String = "",
    val emailError: String? = null,
    val password: String = "",
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val recoverySent: Boolean = false,
    val recoveryMessage: String? = null,
    val isRecovering: Boolean = false,
    val showDevAccountButton: Boolean = false,
)

/**
 * Modelo de vista para el flujo de autenticación y recuperación de contraseña.
 */
class LoginViewModel(
    private val signInUseCase: SignIn,
    private val recoverAccessUseCase: RecoverAccess,
    private val preferences: UiPreferences? = null,
    private val devAccount: DevAccount? = DevAccount.fromBuildConfig(),
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        LoginUiState(
            email = preferences?.lastEmail?.takeIf { it.isNotBlank() } ?: devAccount?.email.orEmpty(),
            showDevAccountButton = devAccount != null,
        ),
    )
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    fun onEmailChange(email: String) {
        mutableState.update {
            it.copy(
                email = email,
                emailError = null,
                errorMessage = null,
                recoveryMessage = null,
            )
        }
    }

    fun onPasswordChange(password: String) {
        mutableState.update {
            it.copy(
                password = password,
                passwordError = null,
                errorMessage = null,
            )
        }
    }

    fun fillDevAccount() {
        val account = devAccount ?: return
        mutableState.update {
            it.copy(
                email = account.email,
                password = account.password,
                emailError = null,
                passwordError = null,
                errorMessage = null,
            )
        }
    }

    fun signIn() {
        val s = mutableState.value
        val email = s.email.trim()
        val password = s.password

        var hasError = false
        var emailErr: String? = null
        var passwordErr: String? = null

        if (email.isBlank()) {
            emailErr = "Ingresa tu correo institucional o personal"
            hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailErr = "Formato de correo no válido"
            hasError = true
        }

        if (password.isEmpty()) {
            passwordErr = "Ingresa tu contraseña"
            hasError = true
        }

        if (hasError) {
            mutableState.update { it.copy(emailError = emailErr, passwordError = passwordErr) }
            return
        }

        mutableState.update { it.copy(isLoading = true, errorMessage = null, recoveryMessage = null) }
        viewModelScope.launch {
            try {
                signInUseCase(email, password)
                preferences?.lastEmail = email
                // El cambio de estado de sesión en AuthPort actualizará automáticamente la raíz de la app
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = formatAuthError(e),
                    )
                }
            }
        }
    }

    fun recoverAccess() {
        val email = mutableState.value.email.trim()
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mutableState.update { it.copy(emailError = "Ingresa un correo válido para recuperar tu acceso") }
            return
        }

        mutableState.update { it.copy(isRecovering = true, errorMessage = null, recoveryMessage = null) }
        viewModelScope.launch {
            try {
                recoverAccessUseCase(email)
                mutableState.update {
                    it.copy(
                        isRecovering = false,
                        recoverySent = true,
                        recoveryMessage = "Se ha enviado un enlace de recuperación a $email",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        isRecovering = false,
                        errorMessage = "No se pudo enviar el correo de recuperación. Verifica la dirección ingresada.",
                    )
                }
            }
        }
    }

    private fun formatAuthError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Invalid login credentials", ignoreCase = true) ->
                "Credenciales incorrectas. Verifica tu correo y contraseña."
            msg.contains("Email not confirmed", ignoreCase = true) ->
                "El correo aún no ha sido confirmado. Revisa tu bandeja de entrada."
            msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) ->
                "No se pudo conectar con el servidor. Revisa tu conexión a internet."
            else -> msg.ifBlank { "Error al iniciar sesión. Inténtalo de nuevo." }
        }
    }
}

