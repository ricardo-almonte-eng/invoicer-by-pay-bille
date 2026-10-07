# 01 — Arquitectura

> **2026-09-16: el proyecto pasó de React Native a Kotlin Multiplatform.** Decisión del dueño del
> proyecto. Todo lo que antes decía Expo/TypeScript/Zustand queda sustituido por lo de abajo.

## Stack

| Pieza | Elección | Por qué |
|---|---|---|
| Plataforma | **Kotlin Multiplatform** — Android + iOS | Un solo código para los dos teléfonos |
| UI | **Compose Multiplatform**, **sin Material** | La UI también se comparte. La identidad de PayBille (islas, bordes de 1 dp, cero sombras) es propia: se construye sobre `foundation`, no se pelea con Material |
| Inyección | **Koin** | Ligero, multiplataforma, sin generación de código |
| Navegación | **Voyager** (+ `voyager-koin`) | Mismo que MsjExpress. `Screen` + `ScreenModel` |
| HTTP | **Ktor** (OkHttp en Android, Darwin en iOS) | El motor lo elige la dependencia de cada plataforma: sin expect/actual |
| JSON | **kotlinx.serialization** | Con serializadores tolerantes para los números-string de la API |
| Base local | **Room KMP** + `sqlite-bundled` | Offline first: la base local es la fuente de verdad |
| "Atrás" | `navigationevent-compose` (`NavigationBackHandler`) | Sustituye al `BackHandler` deprecado de `ui-backhandler` |
| Configuración | **BuildKonfig** (desde `local.properties`) | URL y `API_KEY` fuera del repositorio |

**Versiones:** las de `gradle/libs.versions.toml`, alineadas con MsjExpress (Kotlin 2.4.20,
Compose MP 1.12.0, AGP 9.3.0, Koin 4.2.2, Ktor 3.5.2, Room 2.8.5). Es el proyecto KMP de la casa que
ya compila con este JDK 17 y este Android SDK. Subirlas es una tarea aparte.

## Regla de oro: todo en `commonMain`

Lógica **y** UI viven en `composeApp/src/commonMain`. Solo baja a `androidMain`/`iosMain` lo que
de verdad depende de la plataforma, y siempre con **`expect`/`actual`**. Hoy eso es una sola
cosa:

| `expect` | Android | iOS |
|---|---|---|
| `platformModule` (Koin) | Ruta de la base con `androidContext()` | Ruta en `Application Support` |
| ↳ `DocumentPlatform` | `PdfRenderer` · `FileProvider` + `ACTION_SEND` · `MediaStore` Descargas · HTML→PDF con WebView + `PdfDocument` | PDFKit · `UIActivityViewController` · `UIDocumentPickerViewController` · HTML→PDF con `UIPrintPageRenderer` |
| ↳ `HtmlView` (composable) | `WebView` (`AndroidView`) | `WKWebView` (`UIKitView`) |
| ↳ `ReminderScheduler` | WorkManager + `NotificationCompat` | `UNUserNotificationCenter` |
| `NotificationPermissionRequest` (composable) | `POST_NOTIFICATIONS` (13+) | `requestAuthorization` |
| `rememberImagePicker` (composable) | `TakePicture` a `cache/captures` por FileProvider · `PickVisualMedia`; submuestreo, giro EXIF (`android.media.ExifInterface`), ≤ 1280 px, JPEG 85 o PNG si hay transparencia. **Sin permiso de cámara**: no se declara `CAMERA` (si se declarara, `TakePicture` exigiría pedirlo) | `UIImagePickerController` (cámara / fotos), redibujo que endereza, ≤ 1280 px. `NSCameraUsageDescription` y `NSPhotoLibraryUsageDescription` en `iosApp/Info.plist` |

El toque en un aviso llega a `NotificationRouter` (común): en Android desde el `Intent` de
`MainActivity`; en iOS desde `IosNotificationDelegate`, que registra `MainViewController`.
El `FileProvider`, su `file_paths.xml` y el permiso de avisos están en
`composeApp/src/androidMain` y se fusionan con el manifiesto de `androidApp`.

Antes de añadir otro `actual`, busca si la librería ya lo resuelve (Ktor elige motor solo; Compose
Resources carga fuentes e imágenes en las dos plataformas).

## Mapa de carpetas

```
composeApp/                         Módulo KMP: TODO el código compartido
  build.gradle.kts                  Targets android + iosArm64 + iosSimulatorArm64, BuildKonfig, Room
  schemas/                          Esquemas de Room (versionados: son la base de las migraciones)
  src/commonMain/
    composeResources/
      drawable/                     paybille_isotipo.png
      font/                         google_sans_flex_{regular,medium,semibold,bold}.ttf ·
                                    material_symbols_rounded.ttf (los iconos, eje FILL)
      files/licenses/               Licencia OFL de la fuente
    kotlin/com/paybille/invoicer/
      App.kt                        Raíz: tema + pantalla según la sesión
      core/
        database/                   InvoicerDatabase (Room) · PayloadCache (JSON por clave)
        designsystem/theme/         PbColors, PbTypography, tokens, PbTheme
        designsystem/components/    Pb* — componentes propios (ver 06)
        di/                         Koin: initKoin + expect platformModule
        format/                     Formato de cifras, periodos de fechas, cédula
        ui/                         SyncState + syncStatusItem (estado de carga de las listas)
        network/                    Ktor, PayBilleApi, ApiException, serializadores tolerantes
        billing/                    (fase 2) Tax.kt y Money.kt — ÚNICO sitio de esas cuentas
      feature/
        auth/
          data/local/               Entidades y DAO de la sesión
          data/remote/              DTOs y llamadas de sesión
          data/                     SessionRepository, SessionTokenStore, mapeos
          domain/                   Session, SessionState, LoginResult…
          presentation/             LoginScreen + LoginScreenModel
        sales/                      Ventas: tabla `sales` (Room), SalesRepository, SaleRow
        home/                       Inicio: cabecera + pestañas de ventas
        profile/                    Mi perfil: tienda, sincronización, cerrar sesión
  src/androidMain/                  actual platformModule
  src/iosMain/                      actual platformModule · MainViewController
  src/commonTest/                   Pruebas del contrato con la API (MockEngine)
androidApp/                         Solo Application (arranca Koin) y MainActivity
iosApp/                             Proyecto Xcode: SwiftUI que muestra MainViewController
documentacion/guidelines/           Esto
```

Cada *feature* sigue `data/` → `domain/` → `presentation/`. **`core/network` es la única carpeta
que sabe que existe un servidor**; las pantallas nunca ven Ktor.

## Configuración (`local.properties`, no se versiona)

```properties
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
# La misma API_KEY del .env del POS. Va en el BODY del login como `key`.
paybille.apiKey=<pídesela al dueño del proyecto>
# Opcional. Por defecto: https://api.paybille.com/ventex/api
# paybille.apiBaseUrl=http://10.0.2.2:2001/ventex/api
```

| Clave | Uso |
|---|---|
| `paybille.apiBaseUrl` | API de negocio. La genérica es siempre `{base}/generic` |
| `paybille.apiKey` | Body del login. El backend firma el JWT **con esta clave** (`jsonwebtoken.js → token()`) |

BuildKonfig las convierte en `BuildKonfig.API_BASE_URL` / `API_KEY` al compilar. Cambiarlas
obliga a recompilar.

⚠️ La `API_KEY` **queda dentro del binario**: cualquiera que descomprima el APK la lee. Es igual de
pública que en el POS (Nuxt la expone en `runtimeConfig.public`), así que no empeoramos nada —
pero **no metas ahí ningún secreto nuevo**.

⚠️ Desde el emulador de Android, `localhost` es el propio emulador: la API local está en
`http://10.0.2.2:2001/ventex/api`. Android bloquea `http://` en claro; **solo en debug**
(`androidApp/src/debug/res/xml/network_security_config.xml`) se permite hacia `localhost` y
`10.0.2.2`. Para probar contra la API local sin tocar `local.properties` (2026-10-06):

```bash
adb reverse tcp:2001 tcp:2001
./gradlew :androidApp:installDebug -Ppaybille.apiBaseUrl=http://localhost:2001/ventex/api
```

`-P` solo vale si `local.properties` no trae ya la clave (gana el archivo). La sesión guardada
sirve contra la API local si su `.env` firma con la misma `SECRERTKEY`. Una compilación normal
vuelve a la API de producción.

⚠️ El `.env` de `PayBille_API` puede apuntar a **PAYBILLE_DB_PROD** y `_context.js` puede tener
`sequelize.sync({alter: true})` activo: arrancarla así altera el esquema de **producción**. Para
probar, `DB_DEV_NAME=PAYBILLE_DB_DEV node index.js` (dotenv no pisa una variable ya puesta).

## Arranque y sesión (offline first)

```
Application / MainViewController → initKoin()
App() → SessionRepository.state (Flow desde Room)
   Loading   → lienzo vacío (leer Room tarda milisegundos)
   SignedOut → LoginScreen
   SignedIn  → HomeScreen  → cada pestaña refresca su página 1
                ProfileScreen → refreshProfile() en segundo plano
```

**No hay `navigate()` tras el login ni tras el logout.** La pantalla la decide la fila de
`session` en Room: aparece al entrar y desaparece al salir. Así es imposible quedar en el Inicio
sin sesión.

Login, verificado contra `PayBille_API/src/adapters/controllers/users.js`:

1. `POST {BASE}/users/login?page=1&pageSize=10` con `{ key, username, password, isGet: true }`.
2. La respuesta (dentro de `data`) puede ser:
   - `"Incorrect username or password"` — **string, con HTTP 200**;
   - `{ requiresMarket: true, markets, user }` — **sin token**: el usuario tiene varias tiendas y
     el `IdMarket` viaja dentro del JWT, así que hay que elegir y repetir el paso 1 con
     `IdMarket`;
   - `{ user, token }` — sesión.
3. Cascada del perfil, **en paralelo y con el token explícito** (todavía no está guardado):
   `persons/{IdPerson}`, `roles/{IdRol}`, `markets/{user.IdMarket}`, `get/Settings { IdMarket }`.
   La tienda sale de **`user.IdMarket`**, no de `person.IdMarket`: el POS tuvo un bug por leerla
   de dos sitios (`stores/data/user.js → hydrate()`).
4. Todo se guarda en Room en **una transacción** (`SessionDao.saveLogin`).

El perfil es **mejor esfuerzo**: si la tienda no carga, el login no se pierde; la sesión queda con
`profileSyncedAt = null` y la pantalla de perfil lo reintenta.

**Diferencias deliberadas con el POS:**

- **No se manda `RapidLogin`.** El backend lo escribe en `Users.IndRapidLogin` en cuanto viene,
  *aunque sea `false`* (`repositories/marketByUser.js → setActiveMarket`), y esa preferencia es la
  misma del POS. Mandarlo desde el móvil se la cambiaría a escondidas.
- **No se crea la fila de `Settings`** si falta (el POS sí). Aquí solo se lee.
- **No se replica `checkTorning()`**: no hay turno de caja en móvil.
- ⚠️ La segunda fase **cambia la tienda activa en el servidor** (`Users.IdMarket`). Si el usuario
  tiene el login rápido activado en el POS, el POS entrará después en la tienda elegida aquí.

### Cambio de tienda sin volver a entrar (2026-09-27)

`SessionRepository.switchStore(idMarket)`, desde la hoja de la cabecera:

1. **Si hay documentos en la cola de envíos, no empieza** (`StoreSwitchBlockedException`): se
   enviarían con el token nuevo y acabarían en la otra tienda.
2. `POST marketbyuser/switch { IdMarket }` con el token actual → `{ user, token }` nuevo.
3. La cascada del perfil con el token nuevo (igual que el login).
4. Token nuevo a la caché → `UserDataCleaner.clearStoreData()` (todo lo del negocio, **no** la
   sesión) → `saveLogin`. La sesión no desaparece en ningún momento: no se pasa por el login. Si
   algo falla, la caché vuelve al token anterior.
5. `App` envuelve el Navigator en `key(idMarket)` **y** la pantalla es `MainScreen(idMarket)`,
   con `key = "MainScreen:$idMarket"`. Hacen falta las dos cosas: Voyager guarda los `ScreenModel`
   por `screen.key` (sin el Navigator) y, con una clave fija, el armazón nuevo recogía los modelos
   del viejo justo antes de que el Navigator viejo los desechara — la hoja de tiendas se quedaba
   "cargando" y los destinos no volvían a pedir nada (error del 2026-09-27). Además la hoja se
   cierra sola al terminar el cambio.

El cambio corre en `NonCancellable`: borrar lo local y guardar la sesión nueva van juntos aunque
la pantalla se desmonte a mitad. Igual que la segunda fase del login, **cambia `Users.IdMarket`
en el servidor**: el POS con login rápido abrirá la tienda elegida aquí (la hoja lo avisa).

## La sesión no vence

Decisión del 2026-09-16: **la sesión solo termina cuando el usuario pulsa "Cerrar sesión".**

- El JWT de la API se firma con `expiresIn: '100y'`: en la práctica no caduca.
- **Un 401/403 no cierra la sesión.** Se muestra el error y el usuario decide. (Esto sustituye al
  "401 → logout limpio" que decía la fase 1 del plan.)
- Cerrar sesión borra **todos** los datos del usuario (`UserDataCleaner`: `sales`, y por último
  `session`, `market`, `market_settings`) y pide confirmación. **Cada tabla nueva con datos del
  negocio se añade a `UserDataCleaner`.**

## Dónde vive el token

En la tabla `session` de Room, dentro del almacenamiento privado de la app:

- **Android:** `getDatabasePath()`; `android:allowBackup="false"` para que no salga en copias de
  seguridad.
- **iOS:** `Application Support` (no `Documents`, para que no aparezca en la app Archivos),
  cifrado por Data Protection.

No está cifrado *dentro* del archivo. Si algún día hace falta (dispositivos compartidos, root),
el siguiente paso es cifrar la columna con una clave del Keystore/Keychain vía `expect`/`actual`.

## Base local — reglas

- Al añadir una tabla, **sube `version` y escribe la migración** (para tablas o columnas nuevas
  basta `AutoMigration(from, to)`: así se añadió `sales` en la versión 2). Nada de
  `fallbackToDestructiveMigration`: borraría la sesión del usuario en cada actualización.
- `composeApp/schemas/` se versiona: es lo que permite escribir y probar migraciones.
- La sincronización (cola de envíos pendientes para facturas hechas sin red) llega con la fase 2.
  Cerrar sesión con envíos pendientes **tendrá que avisar**: hoy no hay nada pendiente que perder.

## Decisiones que ya están tomadas (no las vuelvas a abrir)

1. **La API no se toca desde este repo.** Vive en otro proyecto. Si algo falta, se anota como
   *petición al backend* en [11](11-plan-de-implementacion.md).
2. **Nada de escribir `Paid`/`Balance`.** El saldo lo calcula el servidor.
3. **Cero colores literales** fuera de `PbColors` → [05](05-diseno-y-tema.md).
4. **Español, `es-DO`, `America/Santo_Domingo` (UTC-4).** Los textos van en español directamente
   en el código: la app no se traduce.
5. **La verificación en dispositivo la hace el usuario.** Claude compila y corre las pruebas de
   `commonTest`, pero no instala ni arranca la app → [12](12-flujo-de-trabajo.md).
