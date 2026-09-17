# 12 — Flujo de trabajo

Mismo método que en PayBille POS. Funciona; no lo cambies porque este repo sea nuevo.

## Las cinco reglas

### 1. Antes de tocar código, lee la guía que toca

`README.md` de esta carpeta mapea tarea → documento. Leer 200 líneas de guía ahorra media hora de
`grep` y evita reimplementar algo que ya existe.

### 2. En toda tarea se actualiza `CLAUDE.md`

**Antes** de empezar, anota en *Contexto activo* qué vas a hacer. **Al terminar**, cierra la entrada
con el resultado. El objetivo es que la siguiente sesión arranque sin releer el repositorio.

Formato de entrada:

```markdown
### AAAA-MM-DD — Título corto
- **Problema del usuario:** qué le pasaba, en sus palabras.
- **Qué se hizo:** decisiones, no listado de archivos (para eso está git).
- **Por qué así:** la alternativa que se descartó y el motivo. Esto es lo que más vale.
- **Estado:** terminado / a medias / pendiente de verificación del usuario.
- **Qué mirar:** qué debe revisar el usuario, y en qué dispositivos.
```

Reglas del archivo:

- Solo lo que **no** se deduce del código ni del historial de git.
- **Máximo 3 entradas.** Borra las más viejas.
- El detalle permanente va a `documentacion/guidelines/`, no a `CLAUDE.md`.
- `CLAUDE.md` **por debajo de 17 KB**. Si una sección crece, muévela a una guía y deja el enlace.

### 3. Lo que aprendas leyendo, escríbelo

Si descubres algo del backend o de una librería que no estaba documentado, **añádelo a la guía
correspondiente en la misma tarea**. No en la siguiente. La siguiente no llega.

### 4. Claude no ejecuta la aplicación

**Nada de `npx expo start`, ni builds de EAS, ni abrir un simulador.** Arrancar, probar y verificar
lo hace **el usuario**, en su dispositivo.

Al terminar un cambio: entrega el resumen, di **exactamente qué hay que mirar** y en qué
condiciones (tema oscuro, pantalla pequeña, teclado abierto…). Si necesitas confirmar algo que solo
se ve en ejecución, **pídeselo**; no lo supongas y no lo des por hecho en el resumen.

Lo que Claude **sí** puede hacer sin ejecutar la app: comprobar tipos (`tsc --noEmit`), lint,
revisar que los tokens de tema usados existan, y verificar que los nombres de campo coincidan con
[03](03-modelo-de-datos.md).

### 5. La verificación visual se pide explícita

Todo cambio de interfaz se prueba en:

| Caso | Por qué |
|---|---|
| Pantalla pequeña (~360 dp) | Donde se rompen las filas de importes |
| Pantalla grande / tablet | Que no se estire feo |
| Tema oscuro | Cualquier cambio de color |
| Tipografía grande del sistema | Accesibilidad |
| Teclado abierto sobre el formulario | La factura se teclea con medio teclado encima |

Dilo en el resumen. El usuario no adivina qué mirar.

## Cómo se responde una tarea

1. **Confirma el alcance en una línea.** Si la petición admite dos lecturas distintas que dan
   trabajos distintos, pregunta **antes**; si la duda no cambia el resultado, decide y dilo.
2. **Haz el trabajo completo.** Si una parte queda bloqueada, termina el resto y **di
   explícitamente qué falta y por qué**. Recortar el alcance es decisión del usuario.
3. **Resume con lo que importa:** qué cambió, qué decisión se tomó, qué hay que revisar.
4. **Cierra la entrada de `CLAUDE.md`.**

## Contra qué se contrasta

`C:\repos\PayBille_POS` es la referencia viva: **la misma API, el mismo diseño, las mismas reglas
de negocio.** Ante una duda sobre el backend, la respuesta está en su código, no en la
imaginación. Los sitios donde mirar primero:

| Duda | Dónde |
|---|---|
| Forma de un payload | `pages/ventas.vue`, `components/Tools/Modals/Body/completeOrder.vue` |
| Cliente HTTP | `stores/data/fetchData.js` |
| Impuestos | `stores/components/cart.js → recalcTotals()` |
| Cuentas y abonos | `stores/data/accountDocs.js` |
| Tokens de color | `css/Colors.css` |
| Reglas de negocio ya escritas | `documentacion/guidelines/08-reglas-de-negocio.md` |

Y una advertencia: **el POS también tiene deuda.** No todo lo que hay allí es ejemplo a seguir
(imports manuales de stores, colores literales sueltos, `permissions` que no existe). Copia el
**contrato con la API**, no necesariamente el estilo.
