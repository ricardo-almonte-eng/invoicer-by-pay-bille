# Invoicer By PayBille

App móvil de facturación para **negocios pequeños y vendedores independientes** de República
Dominicana. El hermano pequeño de **PayBille POS**: misma API, misma base de datos, misma
identidad visual — pero en el bolsillo, no en el mostrador.

> **Estado: documentación de arranque.** Todavía no hay código.

## Qué hace

- **Facturas** — crear, cobrar, buscar, compartir por WhatsApp
- **Pagos parciales y completos** — con historial de abonos, saldo y planes de cuotas
- **Estatus** — pendiente de pago, pagada, cotización, anulada
- **Inventario** — productos, existencias y avisos de stock bajo
- **Cotizaciones**, **notas de crédito y débito**, **órdenes de compra**
- **Cuentas de dinero** — en qué caja o banco entró cada cobro

Lo que **no** hace: taller, turnos de caja, impresión térmica, restaurante. Eso es el POS.

## Stack

React Native (Expo) · TypeScript · expo-router · Zustand · React Query · axios

## Por dónde empezar

1. Lee **[CLAUDE.md](CLAUDE.md)** — índice y reglas críticas.
2. Lee **[documentacion/guidelines/00-vision-y-alcance.md](documentacion/guidelines/00-vision-y-alcance.md)**
   — qué entra y qué no.
3. Sigue la **[Fase 0 del plan](documentacion/guidelines/11-plan-de-implementacion.md)**.

La documentación completa está en
[`documentacion/guidelines/`](documentacion/guidelines/README.md) — 14 documentos que cubren desde
el contrato con la API hasta los tokens de diseño.

## Configuración

```bash
cp .env.example .env    # y pide las credenciales al dueño del proyecto
npm install
npx expo start
```

| Variable | Uso |
|---|---|
| `EXPO_PUBLIC_BASE_URL` | API de negocio (`https://api.paybille.com/ventex/api`) |
| `EXPO_PUBLIC_BASE_URL_GENERIC` | CRUD genérico |
| `EXPO_PUBLIC_API_KEY` | Va en el body del login como `key` |

## Licencia

Ver [LICENSE](LICENSE).
