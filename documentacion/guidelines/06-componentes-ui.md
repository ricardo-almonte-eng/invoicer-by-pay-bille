# 06 — Componentes de interfaz

Equivalencias con los `Custom*` del POS (`PayBille_POS/components/Custom/`), y lo que hay que
construir. **Antes de crear un componente nuevo, mira si ya está aquí.**

## Primitivas — `components/ui/`

| Componente | Equivale a | Props mínimas |
|---|---|---|
| `<Button>` | `.BtnMain` / `.BtnBicolor` | `variant: 'fill' \| 'outline' \| 'ghost' \| 'danger'`, `loading`, `icon`, `onPress` |
| `<Input>` | `<CustomInput>` | `label`, `value`, `onChangeText`, `keyboardType`, `error`, `icon`, `required` |
| `<MoneyInput>` | — | Teclado numérico, formato al salir del campo, `tabular-nums`. **Úsalo para todo importe** |
| `<Select>` | `<CustomSelect>` | `options`, `value`, `onChange` → se abre como `BottomSheet`, nunca como dropdown |
| `<Textarea>` | `<CustomTextarea>` | |
| `<Checkbox>` / `<Switch>` | `<CustomCheckBox>` | En móvil, `Switch` nativo para ajustes; `Checkbox` solo en listas |
| `<Label>` | `<CustomLabel>` | `value`, `size`, `weight`, `color`, `currency` |
| `<Card>` | `<CustomCard>` | La "isla": fondo `island`, `borderWidth: 1`, `radius.lg`. **Sin sombra** |
| `<Sheet>` | `<ToolsModals>` | `BottomSheet`. Sustituye a todo el sistema de modales del POS |
| `<Toast>` | `<CustomToast>` | `success \| error \| info` |
| `<ConfirmSheet>` | `<CustomDialog>` | Confirmación destructiva |
| `<EmptyState>` | — | Ilustración + frase + acción. Hace más por la app que cualquier otra pieza |
| `<Skeleton>` | `<CustomLoading>` | Carga **dentro** de la lista, no tapando la pantalla |
| `<Tag>` | `<CustomTags>` | Estatus de factura con color semántico |
| `<Icon>` | `<CustomIcon>` | `name`, `size: 'sm' \| 'md' \| 'lg' \| 'xl'`. Hereda el color del contexto |

### Lo que sustituye al `DataGridView`

En el POS, **69 pantallas** consumen `<CustomDataGridView>`: tabla + filtros + paginación +
búsqueda + acciones por fila. En móvil no hay tabla. Su equivalente es:

```
<ListScreen>
  ├── <SearchBar>          búsqueda con debounce → getGeneric(..., like)
  ├── <FilterChips>        4 filtros como máximo, en línea y scrollables
  ├── <FlatList>           + RefreshControl + onEndReached (paginación)
  │     └── <ListRow>      una fila = una tarjeta con 3 datos y un estatus
  └── <EmptyState>
```

**Una fila muestra tres cosas y un estatus. Ni una más.** Para una factura: cliente · total ·
fecha, y el `Tag` de estatus. Todo lo demás está en el detalle.

## Piezas de negocio — `components/domain/`

| Componente | Qué hace |
|---|---|
| `<LineaFactura>` | Una línea del borrador: nombre, cantidad ± , precio, total. Deslizar para borrar |
| `<SelectorProducto>` | Buscador sobre `warehouse` + alta rápida si no existe |
| `<SelectorCliente>` | Buscador `like` sobre `clients` + alta rápida (nombre y teléfono) |
| `<ResumenTotales>` | Subtotal · impuesto · descuento · **Total**. Lee de `lib/tax.ts`, no calcula nada |
| `<PanelCobro>` | Efectivo / transferencia / tarjeta + cuenta destino + faltante |
| `<EstadoFactura>` | El `Tag` con su color: Pagada (`success`), Pendiente (`accent`), Cotización (`muted`), Anulada (`error`) |
| `<ListaAbonos>` | Historial de pagos de un documento con su saldo |
| `<VistaDocumento>` | La factura "en papel" que se convierte en PDF para compartir |

## Reglas de componente

1. **Ninguno hace `fetch`.** Reciben datos por props o usan un hook de `lib/api`. La única
   excepción son los buscadores, que sí llaman a su `useQuery` con debounce.
2. **Ninguno lee colores literales.** Todo por `useTheme()`.
3. **Todo lo pulsable mide 44 pt o más**, aunque el dibujo sea más pequeño (usa `hitSlop`).
4. **Los estilos van en `StyleSheet.create`** fuera del componente, salvo los que dependen del
   tema, que van en una función `estilos(theme)` memoizada.
5. **Nada de sombras.** `borderWidth: 1` → [05](05-diseno-y-tema.md).

## `<VistaDocumento>` y el PDF

El POS imprime en térmica (ESC/POS + `print-agent`). **Aquí el "imprimir" es compartir por
WhatsApp**, y el flujo es:

```
HTML plantilla → expo-print.printToFileAsync() → expo-sharing.shareAsync()
```

Notas del POS que aquí siguen valiendo:

- El logo lo controla `settings.LogoInBill`.
- El título del documento sale del NCF: `B03 → NOTA DE DEBITO`, `B04 → NOTA DE CREDITO`
  (`utils/receipt.js:98`). Reutiliza esa lógica.
- **No reutilices `css/Receipt.css` ni `css/Factura.css`.** Están calibrados al milímetro para papel
  térmico de 58/80 mm y `BillSize`. El PDF de esta app es tamaño carta y es un documento distinto.
