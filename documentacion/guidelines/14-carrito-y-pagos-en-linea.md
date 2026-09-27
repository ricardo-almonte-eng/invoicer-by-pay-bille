# 14 — Carrito y pagos en línea (plan, NO implementado)

> **Estado (2026-09-27): diseño para una fase futura.** Nada de esto existe todavía en la API,
> el POS ni la app. Lo que **sí** existe y sirve de base está en §1. Antes de empezar, relee este
> documento contra el código: pudo cambiar.

El objetivo: que el catálogo público deje de ser una vitrina y pase a ser una tienda. El cliente
arma un **carrito**, paga con tarjeta por una **pasarela** (Stripe, Azul, Cardnet) y el vendedor
recibe la venta ya cobrada en Invoicer y en el POS. Y, por el mismo camino, que el vendedor pueda
mandar un **link de pago** por WhatsApp para cobrar una factura que ya hizo.

---

## 1. Lo que ya existe

| Pieza | Dónde | Qué aporta |
|---|---|---|
| Catálogo público | POS `pages/catalogo/[storeName].vue` → `https://paybille.com/catalogo/<slug>` | La vitrina: búsqueda, categorías, filtros de marca y color, ficha del producto |
| Datos del catálogo | API `POST catalog/token` + `GET catalog/data` (`services/catalog.js`) | JWT corto del catálogo; productos agrupados con **`variants`** (una por fila de `warehouse`, con `warehouseId`, precio, existencia, marca y color) |
| Qué productos salen | `Products.IndShowOnCatalog` (API `F3`) | Invoicer: *Catálogo en línea* y el interruptor de la ficha |
| Dónde pagar | `Sales.PaymentAccounts` / `PaymentNote`, `Cuentas.HolderName` / `HolderId` (API `F4`) | Transferencia manual: el PDF dice a qué cuentas transferir |
| Abonos | `accountdocs` (servidor) | Registrar un pago sobre una factura; **el servidor** mueve saldo y cuenta (regla crítica 5) |
| Notificaciones | Invoicer, campana de Facturas (`feature/notifications`) | Donde aparecerá "Pago recibido" |

**Clave del diseño:** el carrito compra **filas de `warehouse`** (`variants[].warehouseId`), no
productos: un mismo producto puede tener precio y existencia distintos por lote, color o unidad.

---

## 2. Flujo propuesto

```
Cliente (web)                        API                                  Pasarela
─────────────                        ───                                  ────────
catálogo → carrito (localStorage)
"Pagar"  ──── POST catalog/orders ──► valida y RECALCULA precios/stock
                                      crea WebOrder (Pendiente)
                                      crea PaymentLink ──────────────────► sesión de pago alojada
         ◄──────── url de pago ─────── (Stripe Checkout / Azul Payment Page / Cardnet)
paga en la página de la pasarela ─────────────────────────────────────────►
                                      ◄──────── webhook firmado ───────── pago aprobado
                                      idempotente por externalId:
                                      WebOrder → Pagado
                                      crea Sales + salesProducts (Complete)
                                      descuenta warehouse + reportInventory
                                      movimiento en la cuenta de la pasarela
vuelve a /catalogo/<slug>/pedido/<token>  (estado del pedido)
                                                                     Invoicer: sincroniza,
                                                                     campana "Pago recibido"
```

**Link de pago de una factura existente** (Invoicer → "Cobrar con enlace"): mismo `PaymentLink`
pero apuntando a un `accountdoc` con saldo. El webhook registra un **abono** con el mismo servicio
que usa `accountdocs/pay` (nunca escribiendo `Paid`/`Balance` a mano, y sin crear el movimiento de
cuenta desde el cliente: lo crea el servidor).

### Reglas que no cambian

1. **Precios los decide el servidor.** El carrito manda `{ warehouseId, cantidad }`; el total se
   recalcula desde `warehouse.Price1` y `market.taxType/taxValue` con las fórmulas de siempre
   (API: mismo redondeo *half-up* que `core/billing/Tax.kt`). Un precio que viene del navegador no
   se usa nunca.
2. **Moneda base.** El cobro va en la moneda de la tienda (DOP). Regla crítica 12: ninguna tabla
   tiene columna de moneda. Si algún día se cobra en USD, hace falta esa columna antes.
3. **Inventario:** se descuenta **al confirmarse el pago**, no al crear el pedido. Vender sin
   existencia sigue permitido en el mostrador, pero en la web **no**: un cliente que paga algo
   que no hay es un reembolso seguro. El pedido revisa la existencia al crearse y otra vez en el
   webhook; si ya no hay, se marca "Pagado sin existencia" y se avisa al vendedor.
4. **Idempotencia.** El webhook puede llegar dos veces o fuera de orden: `PaymentLinks.ExternalId`
   es `UNIQUE` y el cambio de estado se hace en una transacción con `SELECT … FOR UPDATE`.
5. **Nada de tarjetas en PayBille.** Siempre página de pago **alojada** por la pasarela (el
   número de tarjeta no toca nuestros servidores ni el POS): así el alcance PCI es el mínimo
   (SAQ A). No se integra ningún formulario de tarjeta propio.

---

## 3. Llaves de API en la configuración de la tienda

El usuario pidió que las llaves se guarden **en la configuración de la tienda**. Se hace, con
estas condiciones:

- **Solo escritura.** El formulario (POS *Configuración → Tienda* e Invoicer *Configurar tienda →
  Pagos en línea*) manda la llave; la API la guarda **cifrada** y nunca la devuelve. Al leer, solo
  vuelve `{ provider, enabled, mode: 'test'|'live', last4, updatedAt }`. Para cambiarla, se
  escribe otra.
- **Cifrado en reposo** con AES-256-GCM y una llave maestra en variable de entorno del servidor
  (`PAYMENTS_MASTER_KEY`), nunca en la base ni en el repositorio. Rotarla = volver a cifrar.
- **Nunca en `markets`**: `getMarketBySlug` y el login devuelven la fila de la tienda al público o
  a la app. Van en tabla aparte (§4) que ningún endpoint genérico (`generic/get/*`) puede leer:
  hay que **excluirla de `selectModel`**.
- **Modo prueba primero.** Cada pasarela se activa en `test`; pasar a `live` pide confirmación.
- Qué pide cada una (confirmar con la documentación del proveedor al integrar; cambia):

| Pasarela | Credenciales típicas | Notas |
|---|---|---|
| **Stripe** | Secret key (`sk_…`) y secreto del webhook (`whsec_…`) | Checkout Sessions o Payment Links; webhooks firmados. Verificar que la cuenta de Stripe opere para comercios de RD y en DOP |
| **Azul** (Banco Popular) | Merchant ID, datos del comercio y la llave para firmar la petición | *Payment Page* alojada; la respuesta vuelve firmada y hay que validarla |
| **Cardnet** | Número de comercio / terminal y llaves de la API | Checkout alojado de Cardnet; confirmar el mecanismo de notificación (webhook o consulta) |

---

## 4. Modelo de datos (borrador de `F5_pagos_en_linea.sql`)

```sql
-- Credenciales por tienda. Cifradas; la API nunca las devuelve.
CREATE TABLE `PaymentGateways` (
  `id`            INTEGER NOT NULL AUTO_INCREMENT,
  `IdMarket`      INTEGER NOT NULL,
  `Provider`      ENUM('stripe','azul','cardnet') NOT NULL,
  `Mode`          ENUM('test','live') NOT NULL DEFAULT 'test',
  `Enabled`       TINYINT(1) NOT NULL DEFAULT 0,
  `Credentials`   TEXT NOT NULL,          -- JSON cifrado (AES-256-GCM, base64)
  `Last4`         VARCHAR(4) NULL,        -- para enseñar "••••1234"
  `IdCuenta`      INTEGER NULL,           -- a qué `Cuentas` entra el dinero cobrado
  `createdAt`     DATETIME NOT NULL,
  `updatedAt`     DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_gateway_market_provider` (`IdMarket`, `Provider`)
);

-- Pedido de la web (antes de pagarse no es una venta).
CREATE TABLE `WebOrders` (
  `id`            INTEGER NOT NULL AUTO_INCREMENT,
  `IdMarket`      INTEGER NOT NULL,
  `Token`         VARCHAR(64) NOT NULL,   -- para la página pública del pedido
  `Status`        ENUM('Pendiente','Pagado','Cancelado','Expirado','Reembolsado') NOT NULL,
  `CustomerName`  VARCHAR(255) NOT NULL,
  `CustomerPhone` VARCHAR(30) NOT NULL,
  `CustomerNote`  TEXT NULL,
  `SubTotal`      DECIMAL(12,2) NOT NULL,
  `Tax`           DECIMAL(12,2) NOT NULL,
  `Total`         DECIMAL(12,2) NOT NULL,
  `IdSale`        INTEGER NULL,           -- la venta que se crea al pagar
  `createdAt`     DATETIME NOT NULL,
  `updatedAt`     DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_weborder_token` (`Token`)
);

CREATE TABLE `WebOrderItems` (
  `id`          INTEGER NOT NULL AUTO_INCREMENT,
  `IdWebOrder`  INTEGER NOT NULL,
  `IdWarehouse` INTEGER NOT NULL,
  `IdProduct`   INTEGER NOT NULL,
  `Name`        VARCHAR(255) NOT NULL,    -- foto del nombre y precio al pedir
  `Amount`      DECIMAL(9,2) NOT NULL,
  `Price`       DECIMAL(9,2) NOT NULL,
  `Tax`         DECIMAL(9,2) NOT NULL,
  PRIMARY KEY (`id`)
);

-- Un intento de cobro: de un pedido web o del saldo de una factura.
CREATE TABLE `PaymentLinks` (
  `id`            INTEGER NOT NULL AUTO_INCREMENT,
  `IdMarket`      INTEGER NOT NULL,
  `Provider`      ENUM('stripe','azul','cardnet') NOT NULL,
  `IdWebOrder`    INTEGER NULL,
  `IdAccountDoc`  INTEGER NULL,
  `Amount`        DECIMAL(12,2) NOT NULL, -- moneda base
  `Status`        ENUM('Creado','Pagado','Fallido','Expirado') NOT NULL,
  `Url`           TEXT NOT NULL,
  `ExternalId`    VARCHAR(255) NULL,      -- id de la sesión/transacción en la pasarela
  `ExpiresAt`     DATETIME NULL,
  `PaidAt`        DATETIME NULL,
  `createdAt`     DATETIME NOT NULL,
  `updatedAt`     DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_paymentlink_external` (`Provider`, `ExternalId`)
);
```

Sin `FOREIGN KEY`, como el resto del esquema (API `sql/README.md`).

---

## 5. Endpoints propuestos

| Método y ruta | Auth | Qué hace |
|---|---|---|
| `GET market/payment-gateways` | Token de usuario | Estado de cada pasarela (sin llaves) |
| `PUT market/payment-gateways/:provider` | Token de usuario | Guarda/cambia llaves (solo escritura), modo, activa, cuenta destino |
| `POST catalog/orders` | JWT del catálogo | Crea `WebOrder` + `PaymentLink`; devuelve `{ token, payUrl }`. Límite de peticiones por IP |
| `GET catalog/orders/:token` | Público (token) | Estado del pedido para la página de vuelta |
| `POST payments/links` | Token de usuario | Link de pago del saldo de un `accountdoc` (Invoicer "Cobrar con enlace") |
| `POST payments/webhook/:provider` | Firma de la pasarela | Confirma el pago; idempotente. **Público, sin `auth`**, pero rechaza toda firma inválida |

`GET catalog/data` añade `payments: { enabled: boolean, providers: [...] }` para que el catálogo
sepa si enseña "Pagar" o solo "Pedir por WhatsApp".

---

## 6. Pantallas

**Catálogo (POS, web pública)**
- Botón "Agregar" en la ficha, con selector de variante (color) cuando hay varias.
- Carrito en un panel lateral/hoja: cantidades, total recalculado, "Pagar" (si hay pasarela) y
  siempre "Pedir por WhatsApp" (arma el mensaje con los productos: sirve **sin** pasarela y es la
  primera fase).
- Página del pedido: pagado / pendiente / expirado, con el número de la venta.

**Invoicer**
- *Configurar tienda → Pagos en línea*: una fila por pasarela (estado, modo, `••••1234`),
  formulario de solo escritura y "Cuenta donde entra el dinero".
- Detalle de factura con saldo: **"Cobrar con enlace"** → crea el link y abre Compartir. El link
  también puede salir en el PDF junto a "Dónde pagar" (y en el QR).
- Notificaciones: "Pago recibido de $ X — Pedido web #N" / "Factura #N pagada en línea". Como no
  hay push del servidor, se leen al sincronizar (`WebOrders` y abonos recientes).

---

## 7. Fases sugeridas

1. **Carrito + pedido por WhatsApp.** Sin pasarela ni tablas nuevas: el carrito arma el mensaje.
   Valida la experiencia de compra con clientes reales.
2. **Link de pago de facturas (Stripe, modo prueba).** `PaymentGateways`, `PaymentLinks`, webhook
   y abono. Es el camino más corto a cobrar con tarjeta y reutiliza `accountdocs`.
3. **Checkout del catálogo.** `WebOrders`, `POST catalog/orders`, venta al pagar.
4. **Azul y Cardnet.** Mismo contrato (`PaymentProvider` en la API: `createLink`, `verifyWebhook`,
   `parseEvent`), una implementación por pasarela.

## 8. Qué probar cuando se construya

- Webhook repetido y fuera de orden → una sola venta y un solo abono.
- Precio cambiado en el POS entre el carrito y el pago → cobra lo que dijo el pedido, que es lo
  que el servidor calculó al crearlo.
- Sin existencia al llegar el webhook → pedido marcado y aviso al vendedor.
- Llave inválida o pasarela desactivada → el catálogo solo ofrece WhatsApp.
- Ninguna respuesta de la API (ni `generic/get/*`) devuelve `Credentials`.
- Factura emitida en USD en Invoicer → el link cobra el equivalente en pesos guardado.
