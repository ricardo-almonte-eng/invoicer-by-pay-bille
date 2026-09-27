# 00 — Visión y alcance

## Qué es

**Invoicer By PayBille** es el hermano pequeño y móvil de **PayBille POS**. Una app de Kotlin
Multiplatform (Android + iOS) para que **negocios pequeños y vendedores independientes** de República Dominicana
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
9. **Clientes** — ficha mínima: nombre, teléfono, cédula/RNC, y lo que debe.
10. **Compartir el documento** — PDF o imagen por WhatsApp. Es el "imprimir" del móvil.
11. **Resumen** — el dashboard del POS (ventas, gastos, te deben, más vendidos). Adelantado desde
    v2 a petición del usuario (2026-09-18).
12. **Reportes** — ventas por fecha, productos vendidos, saldos pendientes, histórico del
    inventario. Los avanzados (Resumen Financiero / Vista 360) siguen fuera.
13. **Configuración de la tienda, reducida** (2026-09-27) — lo que sale en la factura (logo,
    nombre, dirección, RNC, teléfono, correo) y el impuesto por defecto. Lo demás, en el POS.
14. **Dónde pagar** (2026-09-27) — en la factura que queda debiendo (o la cotización) se eligen
    una o varias cuentas de banco y unas instrucciones; salen en el PDF. La cuenta lleva el
    titular y su cédula/RNC para las transferencias desde otro banco.
15. **Catálogo en línea** (2026-09-27) — compartir el enlace del catálogo público del POS
    (`paybille.com/catalogo/<tienda>`) y elegir qué productos salen. La vitrina (filtros de
    categoría, marca y color) vive en el POS.
16. **Notificaciones** (2026-09-27) — la campana de Facturas: vencidas, por vencer y lo que no
    se pudo enviar. Se deriva de lo guardado en el teléfono.

### No entra

- **Taller / reparaciones** (`workshop`, garantías, diagnósticos). Explícitamente fuera.
- **Turno y cierre de caja** (`torning`). Es un concepto de mostrador. El campo `Torning` se
  sigue enviando porque la API lo espera → ver [08](08-reglas-de-negocio.md).
- Impresión térmica ESC/POS y `print-agent`.
- Restaurante (mesas, comandas), supermercado, financiamientos con scoring, LinkTree, reportes
  avanzados (Resumen Financiero / Vista 360). (El catálogo público **sí** entró: ver 15.)
- Multi-sucursal y administración de roles, NCF y usuarios: eso se sigue haciendo en el POS.
- **Perfiles de acceso.** La app es de **uso personal, un solo usuario**: no hay roles ni
  permisos que repartir → [08](08-reglas-de-negocio.md) §8.

### Puede entrar después (v2)

- Escáner de código de barras con la cámara.
- Recordatorios de cobro por WhatsApp.
- **Carrito y pagos en línea** (Stripe, Azul, Cardnet) sobre el catálogo, y links de pago de
  facturas → plan completo en [14](14-carrito-y-pagos-en-linea.md).
- ~~Dashboard de ventas del mes.~~ Entró en v1 (Resumen, 2026-09-18).

## Principio rector

> Si una pantalla necesita explicación, está mal. El usuario factura de pie, con una mano y con
> mala señal.

Tres consecuencias de diseño, no negociables:

1. **Crear una factura no puede pasar de una pantalla con scroll.** Cliente, líneas, total,
   cobro.
2. **Nada bloquea por una llamada de red que puede tardar.** La app es **offline first**
   (decisión del 2026-09-16): lee siempre de la base local y sincroniza cuando hay red. El
   borrador vive en el teléfono hasta que el usuario pulsa Guardar → ver [02](02-api-y-fetch.md) § *El patrón de venta base
   NO se replica*.
3. **Los montos siempre visibles y en `tabular-nums`.** El usuario cuenta dinero mirando la
   pantalla.
