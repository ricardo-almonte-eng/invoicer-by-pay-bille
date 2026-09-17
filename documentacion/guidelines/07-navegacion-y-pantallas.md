# 07 — Navegación y pantallas

`expo-router`, rutas por archivo. Cuatro pestañas y nada más: si algo no cabe en cuatro, va dentro
de **Más**.

```
app/
├── _layout.tsx                  Providers: Query · Theme · Session · fuentes
├── (auth)/
│   └── login.tsx
├── (tabs)/
│   ├── _layout.tsx              Tab bar
│   ├── index.tsx                INICIO      · resumen del día + acceso a "Nueva factura"
│   ├── facturas.tsx             FACTURAS    · lista + filtros por estatus
│   ├── inventario.tsx           INVENTARIO  · lista de existencias
│   └── mas.tsx                  MÁS         · el resto
├── factura/
│   ├── nueva.tsx                Editor (también sirve para cotización, NC y ND)
│   ├── [id].tsx                 Detalle: líneas, abonos, saldo, acciones
│   └── [id]/cobrar.tsx          Registrar pago (sheet a pantalla completa)
├── cliente/
│   ├── index.tsx                Lista de clientes
│   └── [id].tsx                 Ficha + su saldo pendiente
├── producto/
│   ├── nuevo.tsx
│   └── [id].tsx
├── cotizaciones.tsx
├── compras/
│   ├── index.tsx                Órdenes de compra
│   ├── nueva.tsx
│   └── [id].tsx                 Detalle + botón Confirmar (entra a inventario)
├── saldos.tsx                   "Te deben" / "Debes" — accountdocs por cliente
├── cuentas.tsx                  Cuentas de dinero y sus movimientos
└── ajustes.tsx                  Tienda, tema, sesión
```

## Las cuatro pestañas

| Pestaña | Qué muestra | Acción principal |
|---|---|---|
| **Inicio** | Vendido hoy · cobrado hoy · te deben · productos por agotarse | Botón grande **Nueva factura** |
| **Facturas** | Lista con chips: Todas · Pendientes · Pagadas · Cotizaciones | Toca una → detalle |
| **Inventario** | Existencias con su cantidad; en rojo las que están por debajo de `MinAmountQty` | **+** alta rápida |
| **Más** | Clientes · Saldos pendientes · Compras · Cuentas · Notas de crédito · Ajustes | — |

**Inicio no es un dashboard.** Cuatro cifras y un botón. Si hace falta más, es que la pantalla se
equivocó de sitio.

## El editor de factura, en detalle

Es **la** pantalla de la app. Una sola, con scroll, en este orden:

```
┌─ Cliente ───────────────  toca → buscador; vacío = "Consumidor final"
├─ Líneas ────────────────  cada una: nombre · cant ± · precio · total
│    + Agregar producto     → buscador de warehouse / alta rápida
├─ Totales ───────────────  subtotal · ITBIS 18 % · descuento · TOTAL (grande)
│    el % es tocable ──────  hoja con la tasa editable, prellenada con la del negocio
│    moneda ───────────────  DOP · USD · EUR   + tasa de cambio (oculta si es DOP)
├─ Cobro ─────────────────  Efectivo | Transferencia | Tarjeta  + cuenta destino
│    Faltante: $ 0.00      ← si > 0, la factura nace "Pagos Pendientes"
├─ (plegado) Comprobante ──  NCF sí/no + tipo · RNC
└─ (plegado) Vencimiento ──  fecha + plan de cuotas
```

Reglas heredadas del rediseño del POS (`PayBille_POS/CLAUDE.md`, 2026-09-01):

- **Lo secundario va plegado**, y **se despliega solo si ya trae valor**. NCF, RNC y notas casi
  nunca se tocan; ocupar pantalla con ellos es el error que se corrigió en el editor de cuentas.
- El **título de la pantalla es dinámico**: "Nueva factura" / "Nueva cotización" / "Nota de
  crédito". Se decide por `useDraftInvoice().tipo` **al montar**, no en un efecto posterior —
  ver el título cambiar delante es un fallo que ya se cometió una vez.
- El botón de guardar es **fijo abajo**, siempre visible, con el total escrito dentro:
  `Cobrar $ 1,250.00`.
- **La tasa de impuesto se toca desde los Totales, no desde un ajuste.** Se enseña siempre junto
  al importe (`ITBIS 18 %`) porque es parte de la cifra que el cliente va a pagar; esconderla en
  un plegado obliga a abrirlo solo para comprobar que está bien. Cambiarla recalcula el total
  delante del usuario → [08](08-reglas-de-negocio.md) §1.
- **La moneda vive al lado del impuesto, y por la misma razón:** las dos deciden la cifra que se
  va a cobrar. Cambiarla **repinta todas las líneas**, no solo el total — si el usuario ve el
  total en euros y los precios en pesos, no se fía de ninguno de los dos.
- **Con moneda extranjera, el total del botón lleva el código**: `Cobrar USD 25.00`. Y debajo, en
  pequeño, el equivalente en pesos. Es la cifra que va a quedar guardada, y ocultarla es
  prepararle una sorpresa al usuario cuando abra el POS → [08](08-reglas-de-negocio.md) §11.

## El detalle de factura

```
Cabecera:  # secuencia · fecha · estatus (Tag)
Cliente:   nombre · teléfono → botón WhatsApp
Líneas:    solo lectura
Totales
Pagos:     historial de abonos + SALDO PENDIENTE (destacado)
Acciones:  [Registrar pago]  [Compartir PDF]  [⋯ Anular · Nota de crédito]
```

**"Registrar pago" solo aparece si hay saldo.** Anular es una acción destructiva: va en el menú
`⋯` y pide confirmación.

## Reglas de navegación

1. **`router.push()` para navegar; `router.replace()` solo tras login o logout.**
2. **Nada de navegación por rol, y nada de ocultar por rol.** El POS redirige según `rol.Name`
   porque reparte cinco perfiles entre varios empleados; **aquí hay un solo usuario, el dueño**, y
   todas las pantallas están siempre disponibles → [08](08-reglas-de-negocio.md) §8. Si algún día
   entran varios usuarios, la puerta se pone aquí, en la navegación, no en cada pantalla.
3. **Un flujo de creación no deja la pantalla hasta confirmar.** Si el usuario retrocede con
   líneas escritas, se le pregunta. El borrador se guarda igual, pero hay que avisar (el POS hace
   lo mismo con `useFormStore().unSavedChanges`).
4. **Deep links**: `invoicer://factura/{id}` para poder abrir una factura desde una notificación
   o desde un mensaje. Vale la pena dejarlo montado desde el principio.
