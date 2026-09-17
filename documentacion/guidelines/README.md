# Guías de Invoicer By PayBille

Documentación de arranque del proyecto. **Es la fuente de verdad mientras no exista código.**
Todo lo que hay aquí está verificado contra el repositorio `PayBille_POS` (misma API, misma
identidad visual); cuando una guía dice "en el POS está en `X:línea`", ese dato se leyó del
código real, no se dedujo.

| Guía | Cuándo leerla |
|---|---|
| [00-vision-y-alcance.md](00-vision-y-alcance.md) | Qué es la app, para quién, qué NO entra |
| [01-arquitectura.md](01-arquitectura.md) | Stack, carpetas, `.env`, decisiones y por qué |
| [02-api-y-fetch.md](02-api-y-fetch.md) | Cliente HTTP, autenticación, endpoints, paginación |
| [03-modelo-de-datos.md](03-modelo-de-datos.md) | Formas reales de cada payload de la API |
| [04-estado-y-stores.md](04-estado-y-stores.md) | Zustand: qué store existe y qué guarda |
| [05-diseno-y-tema.md](05-diseno-y-tema.md) | Tokens portados a RN, tipografía, iconos, reglas |
| [06-componentes-ui.md](06-componentes-ui.md) | Inventario de componentes a construir |
| [07-navegacion-y-pantallas.md](07-navegacion-y-pantallas.md) | Mapa de rutas de `expo-router` |
| [08-reglas-de-negocio.md](08-reglas-de-negocio.md) | Impuestos, estatus, pagos, inventario, NCF |
| [09-documentos.md](09-documentos.md) | Cotización, Nota de Crédito/Débito, Orden de Compra |
| [10-convenciones.md](10-convenciones.md) | Reglas de código obligatorias |
| [11-plan-de-implementacion.md](11-plan-de-implementacion.md) | Fases, hitos y orden de construcción |
| [12-flujo-de-trabajo.md](12-flujo-de-trabajo.md) | Cómo trabajar y mantener esta documentación |
| [13-recursos-de-marca.md](13-recursos-de-marca.md) | Fuentes, logo, iconos: qué copiar y desde dónde |

## Convención de lectura

- **Antes de escribir código**, lee `00`, `01`, `02` y `08`. El resto se consulta bajo demanda.
- Cuando descubras algo del backend que no está aquí, **añádelo a la guía correspondiente en la
  misma tarea**. Es la única forma de que la siguiente sesión no vuelva a investigarlo.
- Lo que ya está documentado en `PayBille_POS/documentacion/guidelines/` **no se copia**: se cita.
