# ADR-005: Interfaz en Jetpack Compose conectada solo al backend real

- **Estado:** aceptada
- **Fecha:** 2026-10-19

## Contexto

El equipo de diseño entregó los mockups móviles (`Diseño de Interfaz móvil.pdf`): Resumen, Mapa,
Misiones (con "Nueva misión") e Historial, con los eventos en la campana de la cabecera. En la
revisión se indicó que:

- todavía no hay logo;
- el inicio de sesión se hace al final del proyecto;
- la interfaz debe conectarse con el backend, no con datos simulados, y vivir en una rama del
  repositorio principal como evidencia de avance.

## Decisión

1. **Compose + Material 3** en `:app` (`ui/`), con navegación por tipos (Navigation 2.10) y un
   `ViewModel` por pantalla. La inyección sigue siendo manual (ADR-002).
2. **Solo adaptadores de Supabase.** No hay simulador. Si `local.properties` no tiene una
   configuración válida, la app muestra el error con su causa.
3. **Sin pantalla de inicio de sesión** por ahora: la app arranca en la selección de domicilio. Los
   casos de uso de sesión siguen en `:core` y en `AppContainer` para conectarlos al final.
4. **Contratos provisionales en `:core`** para plano (`FloorPlanPort`) y misiones
   (`MissionQueryPort`), sin adaptador todavía: la interfaz muestra "pendiente de backend" en lugar de
   inventar datos.
5. **Sin ícono propio** hasta que exista el logo (se usa el ícono por defecto del sistema).
6. **compileSdk 37** (targetSdk 36): Compose 1.12, Lifecycle 2.11 y Navigation 2.10 se publican
   compilados con API 37.

## Consecuencias

- Las pantallas muestran datos reales en cuanto el backend los expone (telemetría, alertas) y
  estados explícitos de "pendiente" en lo que todavía no existe (misiones, plano, calibraciones).
- Cuando backend publique los contratos de mapas y misiones, se agregan sus adaptadores en
  `Adapters.supabase` sin cambiar la estructura de la interfaz.
- `:core` sigue sin depender de Android ni de Compose.
