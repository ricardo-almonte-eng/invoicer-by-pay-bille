# 10 — Convenciones de código

## Obligatorio

1. **HTTP solo por `lib/api`.** Ninguna pantalla importa `axios`. Ni una.
2. **El impuesto se calcula solo en `lib/tax.ts`.** Si ves aritmética de impuesto en un componente,
   es un bug esperando a divergir del backend.
3. **`Number()` en todo lo que venga de la API.** `taxValue`, `Amount`, `Price`, `Total` llegan como
   string. Normaliza en `lib/api`, no en la pantalla.
4. **Cero colores literales.** Todo por `useTheme()` → [05](05-diseno-y-tema.md).
5. **Cero sombras** (`elevation`, `shadowOpacity`). Se separa con `borderWidth: 1`.
6. **`fontFamily` por peso**, nunca `fontWeight` → [05](05-diseno-y-tema.md).
7. **Todo importe con `fontVariant: ['tabular-nums']`.**
8. **Nada pulsable por debajo de 44 pt** (usa `hitSlop` si el dibujo es menor).
9. **Nunca envíes `Paid` ni `Balance`** a `accountdocs`: los rechaza el servidor.
10. **`Gasto IS NULL`** en toda consulta sobre `sales`.
11. **UI en español**, `es-DO`, `America/Santo_Domingo`.

## Nombres

| Cosa | Convención | Ejemplo |
|---|---|---|
| Componente | PascalCase, archivo igual que el componente | `LineaFactura.tsx` |
| Hook | `useAlgo` | `useFacturas` |
| Store Zustand | `useAlgo` en `stores/algo.ts` | `useDraftInvoice` |
| Ruta expo-router | kebab o minúscula | `app/factura/nueva.tsx` |
| Tipo de la API | PascalCase, **igual que la tabla** | `Sale`, `SalesProduct`, `Warehouse` |
| Función | camelCase, verbo primero | `calcularTotales()` |

**Los campos de la API se escriben exactamente como los devuelve el backend**, incluidas sus
rarezas (`idWarehouse`, `taxType`, `unique`, `sold`). Renombrarlos a un estilo bonito obliga a
mantener un mapeo en los dos sentidos y a recordarlo para siempre.

## Idioma del código

- **Código en inglés** (nombres de variables, funciones, tipos).
- **Comentarios y textos de interfaz en español.**
- Los campos de la API son lo que son, y muchos ya vienen en español (`Cotizacion`, `Torning`,
  `Gasto`, `cuentas`, `movimientos`). No los traduzcas.

## TypeScript

- `strict: true`.
- Los tipos de la API viven en `types/api.ts` y son **espejo de** [03](03-modelo-de-datos.md).
- Donde el backend sea irregular, dilo en el tipo: `Amount: string | number`, y normaliza al
  entrar. No mientas con `number` a secas.
- **Sin `any`.** Si algo es desconocido, es `unknown` y se valida.

## Comentarios

Se comenta **por qué**, no **qué**. El estilo de la casa está en el POS y es bueno; imítalo:

```ts
// El NCF se pide lo más tarde posible: un número consumido no se devuelve,
// así que si la creación de la cabecera falla, ese comprobante queda quemado.
```

Un comentario que explica una regla del backend vale más que diez que describen el código de al
lado.

## Git

- Rama por tarea, mensajes en español, imperativo: `Añade registro de abonos parciales`.
- **Un commit no deja la documentación desactualizada** → [12](12-flujo-de-trabajo.md).

## Lo que NO se hace

- No se replica el patrón de "venta base" del POS → [02](02-api-y-fetch.md).
- No se copian `css/Receipt.css` ni `css/Factura.css`: son papel térmico calibrado.
- No se usan `permissions` ni `checkPermission()`: son *stubs* vacíos en el backend.
- **No se ramifica la UI por `rol.Name`.** La app es de un solo usuario →
  [08](08-reglas-de-negocio.md) §8.
- No se inventan estatus de factura. El vocabulario está en [08](08-reglas-de-negocio.md) y es
  compartido con el POS.
