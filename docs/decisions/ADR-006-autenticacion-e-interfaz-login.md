# ADR-006: Autenticación completa con Supabase Auth e integración de interfaz

- **Estado:** aceptada
- **Fecha:** 2026-10-29

## Contexto

En las etapas iniciales del proyecto (ADR-005), la pantalla de inicio de sesión se había pospuesto
para el cierre del desarrollo, empleando un mecanismo temporal de autenticación en compilaciones
de depuración (`DevAccount`) para permitir la evaluación de las políticas de seguridad por fila (RLS)
en Supabase.

Para la versión final y la entrega formal, es indispensable que:
1. La aplicación cuente con una interfaz gráfica formal de inicio de sesión (`LoginScreen`),
   accesible y alineada a la identidad visual *Lunar Base* (§12).
2. Se proporcione soporte para la recuperación de acceso mediante correo electrónico (RF01, HU01).
3. Se ofrezca la opción explícita de cierre de sesión (`SignOut`) en la sección de Ajustes,
   limpiando el ámbito de domicilio y retornando de forma segura a la pantalla de login.
4. Se preserve la capacidad de autocompletado con la cuenta de pruebas (`DevAccount`) en entornos
   de desarrollo para facilitar la evaluación y validación de las entregas.

## Decisión

1. **Implementar `LoginScreen` y `LoginViewModel` en `:app`**:
   - Integración directa con los casos de uso preexistentes de `:core` (`SignIn`, `RecoverAccess`).
   - Validación reactiva de campos de texto (correo electrónico con formato válido y contraseña no vacía).
   - Ergonomía táctil con objetivos mínimos de 48 dp, soporte para IME (acciones de teclado virtual)
     y alternancia para mostrar/ocultar contraseñas.
2. **Actualizar el flujo raíz en `RootViewModel`**:
   - Se reemplaza el estado provisional `NeedsSession` por `RootState.NeedsLogin`.
   - Cuando no existe sesión activa o tras un cierre de sesión, la aplicación muestra inmediatamente
     la pantalla de inicio de sesión.
3. **Cierre de Sesión en `SettingsSheet`**:
   - Se integra la acción `SignOut` en la hoja de ajustes, complementada con la visualización del
     identificador del usuario autenticado.
4. **Optimización en el renderizado**:
   - Limpieza y reciclaje de objetos `Path` mediante `remember` en componentes Canvas (`LineChart`),
     reduciendo la recolección de basura en recomposiciones frecuentes.

## Consecuencias

- La aplicación cumple integralmente con HU01 y RF01 en frontend y backend.
- El usuario puede iniciar sesión, recuperar su contraseña, cambiar de domicilio y cerrar sesión
  de manera fluida y segura.
- Las compilaciones de depuración mantienen la opción de autocompletar credenciales de prueba
  mediante un botón secundario, sin comprometer el código productivo.

