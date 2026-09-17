# 00 — Visión y alcance

## Qué es

**Invoicer By PayBille** es el hermano pequeño y móvil de **PayBille POS**. Una app de React
Native para que **negocios pequeños y vendedores independientes** de República Dominicana
facturen desde el teléfono, cobren en partes, sepan qué les deben y lleven un inventario que no
les estorbe.

No es un POS. El POS vive en el mostrador, con caja, gaveta, impresora térmica y turnos. Esto
vive en el bolsillo de quien vende en la calle, en una ruta o en un local de una sola persona.

**Comparte con PayBille POS la misma API, la misma base de datos y la misma identidad visual.**
Un usuario puede facturar desde el POS por la mañana y desde el teléfono por la tarde: es el
mismo `IdMarket`, las mismas facturas y el mismo inventario.

## Para quién

| Perfil | Qué necesita |
|---|---|
| Vendedor independiente (ruta, delivery, ferias) | Facturar rápido, cobrar en efectivo o transferencia, saber quién le debe |
| Negocio de 1–3 personas | Facturas con NCF, inventario simple, cotizaciones para clientes |
| Usuario que ya tiene PayBille POS | Ver y cobrar desde el teléfono lo que se facturó en el mostrador |

El usuario **no es contable**. En pantalla no se dice "cartera", "antigüedad de saldos" ni
"documento de cuenta": se dice **"Saldos pendientes"**, **"Te deben"**, **"Cuenta por cobrar"**.
Esa regla ya se aplicó en el POS y se hereda aquí. Ver
`PayBille_POS/CLAUDE.md` → Contexto activo, entradas del 2026-09-01.

## Alcance del producto

### Entra (v1)

1. **Facturas** — crear, ver, buscar, anular. Con o sin NCF.
2. **Pagos** — parciales y completos, con historial de abonos y saldo pendiente.
3. **Estatus de factura** — pendiente de pago, pagada, cotización, anulada.
4. **Inventario** — productos con precio, costo y existencia; alta rápida; descuento de
   existencia al facturar.
5. **Cotizaciones** — presupuesto que no toca inventario y que se convierte en factura.
6. **Notas de crédito y de débito** — ajustes sobre una factura ya emitida.
7. **Órdenes de compra** — lo que el vendedor le compra a su suplidor, y que al confirmarse
   entra al inventario.
8. **Cuentas de dinero** — registrar en qué cuenta (caja o banco) entró cada cobro.
9. **Clientes** — ficha mínima: nombre, teléfono, cédula/RNC.
10. **Compartir el documento** — PDF o imagen por WhatsApp. Es el "imprimir" del móvil.

### No entra

- **Taller / reparaciones** (`workshop`, garantías, diagnósticos). Explícitamente fuera.
- **Turno y cierre de caja** (`torning`). Es un concepto de mostrador. El campo `Torning` se
  sigue enviando porque la API lo espera → ver [08](08-reglas-de-negocio.md).
- Impresión térmica ESC/POS y `print-agent`.
- Restaurante (mesas, comandas), supermercado, financiamientos con scoring, catálogo público,
  LinkTree, reportes avanzados (Resumen Financiero / Vista 360).
- Multi-sucursal y administración de roles, NCF y usuarios: eso se sigue haciendo en el POS.
- **Perfiles de acceso.** La app es de **uso personal, un solo usuario**: no hay roles ni
  permisos que repartir → [08](08-reglas-de-negocio.md) §8.

### Puede entrar después (v2)

- Modo sin conexión con cola de sincronización.
- Escáner de código de barras con la cámara.
- Recordatorios de cobro por WhatsApp.
- Dashboard de ventas del mes.

## Principio rector

> Si una pantalla necesita explicación, está mal. El usuario factura de pie, con una mano y con
> mala señal.

Tres consecuencias de diseño, no negociables:

1. **Crear una factura no puede pasar de una pantalla con scroll.** Cliente, líneas, total,
   cobro.
2. **Nada bloquea por una llamada de red que puede tardar.** El borrador vive en el teléfono
   hasta que el usuario pulsa Guardar → ver [02](02-api-y-fetch.md) § *El patrón de venta base
   NO se replica*.
3. **Los montos siempre visibles y en `tabular-nums`.** El usuario cuenta dinero mirando la
   pantalla.
