# 11 — Plan de implementación

Orden pensado para que **haya algo usable en la fase 2** y todo lo demás se apoye encima.

---

## Fase 0 — Andamiaje

- [ ] `npx create-expo-app@latest invoicer --template` con TypeScript y expo-router.
- [ ] Dependencias: `axios`, `zustand`, `@tanstack/react-query`, `expo-secure-store`,
      `@react-native-async-storage/async-storage`, `react-hook-form`, `zod`, `dayjs`,
      `expo-font`, `expo-print`, `expo-sharing`, `@expo/vector-icons`.
- [ ] `.env` con `EXPO_PUBLIC_*` → [01](01-arquitectura.md). `.env` al `.gitignore`;
      `.env.example` versionado.
- [ ] `theme/` con los tokens de [05](05-diseno-y-tema.md) + `ThemeProvider` + `useTheme`.
- [ ] Fuentes: TTF estáticos de Google Sans Flex → [13](13-recursos-de-marca.md).
- [ ] `lib/api/client.ts` con los interceptores de [02](02-api-y-fetch.md).
- [ ] `types/api.ts` a partir de [03](03-modelo-de-datos.md).
- [ ] `lib/tax.ts`, `lib/format.ts` y `lib/money.ts` (las dos conversiones de moneda, con test
      por dirección → [08](08-reglas-de-negocio.md) §11).

**Se termina cuando:** la app arranca, muestra una pantalla con los colores de PayBille en claro y
oscuro, y con la tipografía correcta.

---

## Fase 1 — Sesión

- [ ] Pantalla de login (usuario/contraseña, con el `key` de la API en el body).
- [ ] Token en SecureStore + cascada `persons → roles → markets → Settings`.
- [ ] `useSession` con `taxRate()` normalizado.
- [ ] Arranque: con token → pestañas; sin token → login. Cerrar sesión.
- [ ] Manejo del token caducado (401 → logout limpio, sin pantalla en blanco).

**Se termina cuando:** el usuario entra con su cuenta de PayBille y ve el nombre de su tienda.

---

## Fase 2 — Facturar (**el MVP de verdad**)

- [ ] `useDraftInvoice` persistido en AsyncStorage.
- [ ] Buscador de productos sobre `warehouse` (`like` + paginación).
- [ ] Editor de factura completo → [07](07-navegacion-y-pantallas.md).
- [ ] Selector de **tasa de impuesto** y de **moneda + tasa de cambio** en los Totales.
      Al guardar: importes en **moneda base**; moneda y tasa se archivan en AsyncStorage contra
      el `id` de la venta, que es lo único que permite reimprimir el PDF.
- [ ] `lib/tax.ts` conectado a `<ResumenTotales>`.
- [ ] Guardado con la secuencia de 8 pasos de [02](02-api-y-fetch.md), con reintento y borrador
      a salvo si falla.
- [ ] Descuento de inventario + `reportInventory`, con **aviso saltable** si no alcanza la
      existencia (puede quedar negativa) → [08](08-reglas-de-negocio.md) §4.
- [ ] Lista de facturas con filtros por estatus y **`Gasto IS NULL`**.
- [ ] Detalle de factura.
- [ ] Compartir PDF por WhatsApp (`expo-print` + `expo-sharing`).

**Se termina cuando:** el usuario factura desde el teléfono y la factura aparece igual en el POS.
A partir de aquí la app ya sirve para algo, y todo lo demás se puede probar contra uso real.

---

## Fase 3 — Cobrar

- [ ] `from-sale` al guardar una factura con saldo.
- [ ] Pantalla de detalle con abonos y saldo.
- [ ] Registrar abono (`{id}/payments`) con método, cuenta y referencia.
- [ ] Pantalla **Saldos pendientes**: `accountdocs/byparty` (quién te debe y cuánto).
- [ ] Planes de cuotas: crear y ver el calendario.
- [ ] Anular abono. **No es acción de administrador** (la app no tiene roles), pero sí
      destructiva: menú `⋯` y confirmación.

**Cuidado aquí:** no crear movimiento de cuenta al abonar; lo hace el servidor
([08](08-reglas-de-negocio.md), regla 3).

---

## Fase 4 — Inventario y clientes

- [ ] Lista de existencias con aviso de stock bajo (`Amount <= MinAmountQty`).
- [ ] Alta rápida: producto + warehouse en un solo formulario (como `ProductQuickCreate`).
- [ ] Editar precio, costo y existencia, con su `reportInventory`.
- [ ] Ajuste manual de inventario (entrada/salida con motivo).
- [ ] Clientes: lista, alta rápida, ficha con su saldo.

---

## Fase 5 — Los otros documentos

- [ ] Cotizaciones: lista, alta y conversión a factura → [09](09-documentos.md).
- [ ] Órdenes de compra: alta, "En camino", confirmar recepción, cuenta por pagar.
- [ ] Notas de crédito y débito por la vía C (abono / documento en `accountdocs`), **con el aviso
      en pantalla de lo que esa vía no cubre**.
- [ ] Cuentas de dinero: lista, balance y movimientos.

---

## Fase 6 — Pulido

- [ ] Estados vacíos con ilustración en todas las listas.
- [ ] Tipografía grande del sistema (accesibilidad).
- [ ] Icono de la app y pantalla de arranque con la marca PayBille.
- [ ] Build con EAS y distribución interna para pruebas del usuario.

---

## Peticiones al backend

Ordenadas por cuánto simplifican esta app. **Ninguna bloquea el MVP**, pero las dos primeras se
notan mucho.

| # | Petición | Por qué |
|---|---|---|
| 1 | **`POST /sales/complete`** — cabecera + líneas en una transacción, resolviendo secuencia, NCF, inventario y documento espejo | Elimina la secuencia de 8 llamadas y el riesgo de factura a medias con mala señal. Es *la* mejora |
| 2 | **`POST /sales/{id}/credit-note`** — nota de crédito como entidad, enlazada a la factura, con opción de devolver inventario | Hoy las NC/ND no existen y hay que aproximarlas → [09](09-documentos.md) |
| 3 | **`GET /sales/{id}/full`** — factura con sus líneas, abonos y saldo en una llamada | Hoy son 3 llamadas para pintar un detalle |
| 4 | Confirmar los valores reales de **`cuentas.Type`** | La documentación dice `Efectivo\|Banco\|Tarjeta`, el código filtra por `'Caja'` |
| 5 | Un endpoint de **resumen del día** por `IdMarket` | La pantalla de Inicio hoy tendría que sumar en el cliente |
| 6 | **`Currency` (ISO 4217) + `ExchangeRate` en `sales`** | Hoy la moneda de emisión no se puede guardar: vive en el teléfono y se pierde al reinstalar. Es lo único que impide que el POS enseñe la factura como se emitió → [08](08-reglas-de-negocio.md) §11 |

## Decisiones de producto (cerradas el 2026-09-06)

Las cuatro que estaban pendientes ya las decidió el dueño del proyecto. Se recogen aquí con su
porqué; la regla operativa vive en la guía que toca.

| # | Decisión | Dónde se aplica |
|---|---|---|
| 1 | **Moneda base `$`** (DOP), separadores `es-DO` → `$ 1,250.00` | `lib/format.ts` → [04](04-estado-y-stores.md) |
| 2 | **La app sí crea productos**: alta rápida con nombre, precio y cantidad, y código de barras automático | Fase 4 → [03](03-modelo-de-datos.md) |
| 3 | **NCF apagado por defecto**, se activa factura a factura | [08](08-reglas-de-negocio.md) §5 |
| 4 | **Vender sin existencia avisa pero deja pasar**; la existencia puede quedar negativa | [08](08-reglas-de-negocio.md) §4 |
| 5 | **La tasa de impuesto se elige por factura**, prellenada con la del negocio | [08](08-reglas-de-negocio.md) §1 |
| 6 | **La moneda se elige por factura** (`DOP`/`USD`/`EUR`) con **tasa de cambio manual**; se guarda en base y se emite en la elegida | [08](08-reglas-de-negocio.md) §11 |

**Y una decisión más que no estaba en ninguna lista: la app no tiene roles.** Es de **uso personal,
un solo usuario**; no hay perfiles que repartir. `session.rol` se guarda porque la cascada de
login lo trae, pero **ninguna pantalla se ramifica por él** y no existe la pantalla de "sin
permiso" → [08](08-reglas-de-negocio.md) §8. Simplifica la fase 3 (anular abono deja de ser
acción de administrador) y la 5 (Compras y Cuentas se ven siempre).

### Lo que se verificó en el POS antes de decidir

- **Moneda.** El papel del POS ya imprime `$ 600.00`: `utils/receipt.js` usa
  `formatCash(v, symbol = '$')` con `LOCALE = 'es-DO'`. La pantalla formatea con `en-US`/`USD`.
  Es decir, el POS dice `$` en los dos sitios; el móvil diciendo `RD$` habría sido el único
  disidente. **No era una incoherencia del POS, como suponía la versión anterior de esta guía.**
- **NCF.** `completeOrder.vue:529` arranca con `withNCF = ref(false)` y rotula *"Esta venta no
  llevará comprobante fiscal"*. Apagado por defecto es replicar el POS, no apartarse de él.
- **Sin existencia.** `productOptions.vue:286` corta con `return` y el mensaje *"Cantidad
  insuficiente de productos disponibles"*. Aquí se decide **no** replicar ese bloqueo.
- **Alta rápida.** `ProductQuickCreate.vue` son 640 líneas, pero solo tres campos son
  obligatorios (`Name`, `Price1`, `Amount`) y el código de barras se genera con
  `randomBarcode()`. La versión móvil cabe en una pantalla.

### Efectos secundarios pendientes de resolver al programar

1. **Venta a crédito + NCF apagado.** El POS fuerza el NCF cuando la venta es a crédito
   (`completeOrder.vue:748`) pero puede pedirlo con `tipoNCF: ""`. Con el NCF apagado por
   defecto, en Invoicer eso pasaría constantemente: **hay que preguntar el tipo antes de guardar
   una factura a crédito** → [08](08-reglas-de-negocio.md) §5.
2. **Inventario negativo en la UI.** La lista de existencias necesita un tercer estado además de
   "normal" y "stock bajo": **negativo**, que se resalta como aviso y no como error.
3. **La moneda de emisión no tiene dónde vivir en el servidor** (petición 6). Hasta que exista,
   se archiva en el teléfono contra el `id` de la venta y **el PDF es el único registro**: si se
   reinstala la app, esa factura se reimprime en moneda base. Hay que decirlo en pantalla al
   emitir en moneda extranjera, no descubrirlo tres meses después.
4. **Impuesto y moneda son dos entradas nuevas al mismo cálculo**, y las dos llegan como string
   desde un input. `Number()` en las dos, y las conversiones **solo** en `lib/money.ts`: la
   dirección invertida no lanza error, solo produce un número creíble y equivocado.
