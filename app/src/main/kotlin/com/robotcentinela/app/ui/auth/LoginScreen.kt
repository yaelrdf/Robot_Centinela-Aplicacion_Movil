package com.robotcentinela.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotcentinela.app.R
import com.robotcentinela.app.ui.app.uiDependencies
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.ErrorState
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.model.StatusTone
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing
import com.robotcentinela.app.ui.components.VisorMark
import com.robotcentinela.app.ui.theme.ThemePreference

/**
 * Pantalla de inicio de sesión (HU01, RF01).
 *
 * Sigue los lineamientos de diseño e identidad Lunar Base (§12), tipografía Poppins,
 * adaptabilidad móvil/tableta y transición fluida entre tema claro, oscuro y del sistema.
 */
@Composable
fun LoginScreen() {
    val deps = uiDependencies
    val vm: LoginViewModel = viewModel {
        LoginViewModel(
            signInUseCase = deps.container.signIn,
            recoverAccessUseCase = deps.container.recoverAccess,
            preferences = deps.preferences,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val themePref by deps.preferences.theme.collectAsStateWithLifecycle()
    val colors = CentinelaTheme.colors
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Cabecera institucional con esquinas inferiores suavizadas
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(colors.chrome)
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        ) {
            // Botón de alternancia rápida de tema (Sistema / Claro / Oscuro)
            TextButton(
                onClick = {
                    val next = when (themePref) {
                        ThemePreference.System -> ThemePreference.Light
                        ThemePreference.Light -> ThemePreference.Dark
                        ThemePreference.Dark -> ThemePreference.System
                    }
                    deps.preferences.setTheme(next)
                },
                shape = CentinelaShapes.pill,
                contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.xs),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                AppIcon(
                    when (themePref) {
                        ThemePreference.System -> R.drawable.ic_contrast
                        ThemePreference.Light -> R.drawable.ic_light_mode
                        ThemePreference.Dark -> R.drawable.ic_dark_mode
                    },
                    size = 18.dp,
                    tint = colors.onChrome,
                    contentDescription = "Cambiar tema: actual ${themePref.label}",
                )
                Spacer(Modifier.size(Spacing.xs))
                Text(themePref.label, style = MaterialTheme.typography.labelSmall, color = colors.onChrome)
            }

            Column(
                // Deja libre la fila del selector de tema (objetivo táctil de 48 dp) para que no se encime.
                Modifier.fillMaxWidth().padding(top = CentinelaShapes.minTouchTarget),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                VisorMark(size = 88.dp)

                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onChrome,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.visor_lema),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onChrome.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Contenedor adaptable para móvil y tableta
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.xl)
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = Spacing.xl,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                "Iniciar Sesión",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                "Ingresa con tu cuenta autorizada para consultar los domicilios vinculados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }

                        // Mensaje de error general animado
                        AnimatedVisibility(
                            visible = state.errorMessage != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            state.errorMessage?.let { error ->
                                ErrorState(message = error)
                            }
                        }

                        // Mensaje informativo de recuperación enviada
                        AnimatedVisibility(
                            visible = state.recoveryMessage != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            state.recoveryMessage?.let { info ->
                                Surface(
                                    color = colors.operational.copy(alpha = 0.20f),
                                    border = BorderStroke(CentinelaShapes.borderWidth, colors.operational),
                                    shape = CentinelaShapes.control,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(Spacing.m),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                    ) {
                                        AppIcon(R.drawable.ic_check_circle, tint = colors.graphic(StatusTone.Operational))
                                        Text(
                                            info,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textPrimary,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }

                        // Campo de Correo Electrónico
                        OutlinedTextField(
                            value = state.email,
                            onValueChange = vm::onEmailChange,
                            label = { Text("Correo electrónico") },
                            placeholder = { Text("ejemplo@correo.com") },
                            singleLine = true,
                            leadingIcon = {
                                AppIcon(R.drawable.ic_mail, tint = colors.textSecondary)
                            },
                            isError = state.emailError != null,
                            supportingText = state.emailError?.let { err ->
                                { Text(err, color = colors.critical) }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            ),
                            shape = CentinelaShapes.control,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.focus,
                                unfocusedBorderColor = colors.border,
                                errorBorderColor = colors.critical,
                                focusedLabelColor = colors.primary,
                                unfocusedLabelColor = colors.textSecondary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Campo de Contraseña
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = vm::onPasswordChange,
                            label = { Text("Contraseña") },
                            singleLine = true,
                            leadingIcon = {
                                AppIcon(R.drawable.ic_lock, tint = colors.textSecondary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    AppIcon(
                                        if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                        tint = colors.textSecondary,
                                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = state.passwordError != null,
                            supportingText = state.passwordError?.let { err ->
                                { Text(err, color = colors.critical) }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    vm.signIn()
                                },
                            ),
                            shape = CentinelaShapes.control,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.focus,
                                unfocusedBorderColor = colors.border,
                                errorBorderColor = colors.critical,
                                focusedLabelColor = colors.primary,
                                unfocusedLabelColor = colors.textSecondary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Botón de Inicio de Sesión
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                vm.signIn()
                            },
                            enabled = !state.isLoading,
                            shape = CentinelaShapes.control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                            ),
                            contentPadding = PaddingValues(vertical = Spacing.m),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = CentinelaShapes.minTouchTarget),
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = colors.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.size(Spacing.s))
                                Text("Iniciando sesión…")
                            } else {
                                Text("Ingresar", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // Recuperación de Acceso
                        TextButton(
                            onClick = vm::recoverAccess,
                            enabled = !state.isRecovering,
                            shape = CentinelaShapes.control,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .heightIn(min = CentinelaShapes.minTouchTarget),
                        ) {
                            if (state.isRecovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = colors.primary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.size(Spacing.s))
                            }
                            Text(
                                "¿Olvidaste tu contraseña?",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.primary,
                            )
                        }

                        // Acceso rápido para entorno de pruebas / desarrollo
                        if (state.showDevAccountButton) {
                            OutlinedButton(
                                onClick = vm::fillDevAccount,
                                shape = CentinelaShapes.control,
                                border = BorderStroke(CentinelaShapes.borderWidth, colors.border),
                                contentPadding = PaddingValues(vertical = Spacing.s, horizontal = Spacing.m),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = CentinelaShapes.minTouchTarget),
                            ) {
                                AppIcon(R.drawable.ic_science, tint = colors.textSecondary)
                                Spacer(Modifier.size(Spacing.s))
                                Text(
                                    "Autocompletar cuenta de prueba",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.s))
                Text(
                    "UPIICSA · IPN · Proyecto Interdisciplinario",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
