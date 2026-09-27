# Invoicer By PayBille

App móvil de facturación para **negocios pequeños y vendedores independientes** de República
Dominicana. El hermano pequeño de **PayBille POS**: misma API, misma base de datos, misma
identidad visual — pero en el bolsillo, no en el mostrador.

> **Estado:** fase 1 — login y sesión. Pendiente de verificar en dispositivo.

## Qué hace

- **Facturas** — crear, cobrar, buscar, compartir por WhatsApp
- **Pagos parciales y completos** — con historial de abonos, saldo y planes de cuotas
- **Estatus** — pendiente de pago, pagada, cotización, anulada
- **Inventario** — productos, existencias y avisos de stock bajo
- **Cotizaciones**, **notas de crédito y débito**, **órdenes de compra**
- **Cuentas de dinero** — en qué caja o banco entró cada cobro

Lo que **no** hace: taller, turnos de caja, impresión térmica, restaurante. Eso es el POS.

## Stack

Kotlin Multiplatform (Android + iOS) · Compose Multiplatform con componentes propios · Koin ·
Voyager · Ktor · Room · **offline first**

Lógica y UI se comparten en `composeApp/src/commonMain`.

## Por dónde empezar

1. Lee **[CLAUDE.md](CLAUDE.md)** — índice y reglas críticas.
2. Lee **[00 — Visión y alcance](documentacion/guidelines/00-vision-y-alcance.md)** y
   **[01 — Arquitectura](documentacion/guidelines/01-arquitectura.md)**.
3. Sigue el **[plan](documentacion/guidelines/11-plan-de-implementacion.md)**.

## Configuración

Crea `local.properties` en la raíz (no se versiona):

```properties
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
paybille.apiKey=<la API_KEY del .env del POS>
# paybille.apiBaseUrl=https://api.paybille.com/ventex/api
```

| Clave | Uso |
|---|---|
| `paybille.apiKey` | Va en el body del login como `key` |
| `paybille.apiBaseUrl` | Opcional. Por defecto la API de producción |

## Ejecutar

- **Android:** abre el proyecto en Android Studio y ejecuta la configuración `androidApp`.
- **iOS:** en un Mac, abre `iosApp/iosApp.xcodeproj` en Xcode, pon tu `TEAM_ID` en
  `iosApp/Configuration/Config.xcconfig` y ejecuta. Xcode llama a Gradle para compilar
  `ComposeApp.framework`.

```bash
./gradlew :composeApp:testAndroidHostTest
```

## Licencia

Ver [LICENSE](LICENSE). Google Sans Flex: SIL OFL 1.1. Material Symbols: Apache 2.0.
