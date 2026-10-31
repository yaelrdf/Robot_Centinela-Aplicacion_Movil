# ADR-009: El mapa asistido 2D queda fuera de esta versión

- **Estado:** aceptada
- **Fecha:** 2026-10-31

## Contexto

El mapa asistido 2D (plano, pose con incertidumbre, ruta, habitaciones y estaciones; HU05, HU06, HU19,
RF11) depende de que el equipo de IoT publique el plano validado y el contrato de `map_versions`. No se
implementará en esta versión. Mientras tanto la app dibujaba una cuadrícula con la trayectoria y notas de
"contrato pendiente", que daban la impresión de una función a medias.

## Decisión

1. Se elimina de la app todo lo del mapa 2D: `FloorPlanView`, `RoomDetailSheet`, el estado y la carga del
   plano, la trayectoria, el marcador de posición, la escala de señal, la trama de zonas sin explorar y las
   lecturas "por habitación".
2. Donde estaba el mapa se muestra la leyenda **"No disponible / Implementación futura"**
   (`FutureFeatureNotice`): tarjeta del Resumen, pestaña Mapa, mapa de la misión, "Por habitación" del
   Historial y elección de estaciones en Nueva misión.
3. En `:core` se retiran `FloorPlan`, `FloorPlanPort` y `QueryFloorPlan`. Se conservan `Pose`,
   `MapVersionId` y `StationId`, porque forman parte del frame y del resumen de misión.

## Consecuencias

- La pestaña Mapa sigue en la navegación (como en los mockups) pero solo muestra la leyenda.
- La pose sigue llegando en los frames y se conserva en el dominio, pero la app no la muestra.
- Cuando IoT publique el plano habrá que reintroducir el puerto, el caso de uso y la vista; el diseño
  anterior está en el historial de git (commits previos a este ADR).
