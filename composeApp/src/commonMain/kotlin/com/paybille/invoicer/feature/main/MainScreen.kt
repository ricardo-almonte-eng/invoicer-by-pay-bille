package com.paybille.invoicer.feature.main

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.components.PbAppBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import com.paybille.invoicer.resources.Res
import com.paybille.invoicer.resources.paybille_isotipo
import org.jetbrains.compose.resources.painterResource
import com.paybille.invoicer.core.designsystem.theme.PbMotion
import com.paybille.invoicer.feature.auth.domain.Session
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbBottomBar
import com.paybille.invoicer.core.designsystem.components.PbBottomBarItem
import com.paybille.invoicer.core.designsystem.components.PbFab
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.platform.NotificationPermissionRequest
import com.paybille.invoicer.core.platform.NotificationRouter
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.banks.presentation.BankAccountDetailScreen
import com.paybille.invoicer.feature.banks.presentation.BankAccountEditorScreen
import com.paybille.invoicer.feature.banks.presentation.BanksScreenModel
import com.paybille.invoicer.feature.banks.presentation.BanksTab
import com.paybille.invoicer.feature.clients.presentation.ClientDetailScreen
import com.paybille.invoicer.feature.clients.presentation.ClientEditorScreen
import com.paybille.invoicer.feature.clients.presentation.ClientsScreenModel
import com.paybille.invoicer.feature.clients.presentation.ClientsTab
import com.paybille.invoicer.feature.detail.data.ReceivablesRepository
import com.paybille.invoicer.feature.detail.presentation.SaleDetailScreen
import com.paybille.invoicer.feature.invoice.data.InvoiceSender
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.presentation.InvoiceEditorScreen
import com.paybille.invoicer.feature.invoices.InvoicesScreenModel
import com.paybille.invoicer.feature.invoices.InvoicesTab
import com.paybille.invoicer.feature.products.presentation.ProductDetailScreen
import com.paybille.invoicer.feature.products.presentation.ProductEditorScreen
import com.paybille.invoicer.feature.products.presentation.ProductsScreenModel
import com.paybille.invoicer.feature.products.presentation.ProductsTab
import com.paybille.invoicer.feature.profile.ProfileScreen
import com.paybille.invoicer.feature.reports.presentation.ReportScreen
import com.paybille.invoicer.feature.reports.presentation.ReportsTab
import com.paybille.invoicer.feature.sales.domain.SalesFilter
import com.paybille.invoicer.feature.summary.SummaryScreenModel
import com.paybille.invoicer.feature.summary.SummaryTab
import org.koin.compose.koinInject
import androidx.compose.runtime.remember
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.feature.catalog.presentation.CatalogScreen
import com.paybille.invoicer.feature.notifications.data.NoticesRepository
import com.paybille.invoicer.feature.notifications.presentation.NotificationsBell
import com.paybille.invoicer.feature.notifications.presentation.NotificationsScreen
import kotlinx.coroutines.flow.map

/**
 * Destinos de la barra inferior, en orden. **Facturas es el principal**: la app abre ahí y
 * *atrás* vuelve ahí. Las etiquetas son de una palabra: caben seis a 360 dp.
 */
private enum class MainTab(val item: PbBottomBarItem) {
    Invoices(PbBottomBarItem("Facturas", PbSymbols.ReceiptLong)),
    Summary(PbBottomBarItem("Resumen", PbSymbols.Monitoring)),
    Products(PbBottomBarItem("Productos", PbSymbols.Inventory2)),
    Clients(PbBottomBarItem("Clientes", PbSymbols.Group)),
    Reports(PbBottomBarItem("Reportes", PbSymbols.BarChart)),
    Banks(PbBottomBarItem("Bancos", PbSymbols.AccountBalance)),
}

/**
 * Armazón de la app con sesión: contenido del destino, barra inferior y botón "+".
 *
 * Los destinos no son pantallas de Voyager: cambian dentro de esta, así que "atrás" no
 * recorre el historial. Desde otro destino, "atrás" vuelve a Facturas; desde Facturas, sale
 * de la app (comportamiento estándar de Android).
 *
 * El "+" está en **todos** los destinos: facturar es la función principal de la app.
 *
 * Una instancia POR TIENDA: Voyager guarda los `ScreenModel` por `key` de la pantalla, y con una
 * clave fija el armazón nuevo recogía los modelos del viejo justo antes de que el Navigator viejo
 * los desechara (la hoja de tiendas se quedaba "cargando" y los destinos no volvían a pedir nada).
 */
data class MainScreen(val idMarket: Int) : Screen {
    override val key: String = "MainScreen:$idMarket"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val invoicesModel = koinScreenModel<InvoicesScreenModel>()
        val summaryModel = koinScreenModel<SummaryScreenModel>()
        val productsModel = koinScreenModel<ProductsScreenModel>()
        val clientsModel = koinScreenModel<ClientsScreenModel>()
        val banksModel = koinScreenModel<BanksScreenModel>()
        val sender = koinInject<InvoiceSender>()
        val receivables = koinInject<ReceivablesRepository>()
        val sessions = koinInject<SessionRepository>()
        // Al entrar: vaciar la cola de envíos y reprogramar los avisos con lo guardado (sin red
        // también funciona); luego, si hay red, traer los vencimientos al día.
        LaunchedEffect(Unit) {
            sender.trigger()
            val idMarket = sessions.currentSession()?.idMarket ?: return@LaunchedEffect
            runCatching { receivables.reschedule(idMarket) }
            runCatching { receivables.refresh(idMarket) }
        }

        // Avisos de vencimiento: se pide permiso una vez (Android 13+ e iOS). Sin permiso la
        // app funciona igual, solo que no avisa.
        NotificationPermissionRequest { }

        // Se tocó un aviso: se abre esa factura.
        val saleFromNotification by NotificationRouter.saleToOpen.collectAsState()
        LaunchedEffect(saleFromNotification) {
            saleFromNotification?.let {
                navigator.push(SaleDetailScreen(saleId = it))
                NotificationRouter.consumed()
            }
        }
        var tab by rememberSaveable { mutableStateOf(MainTab.Invoices) }
        var invoicesFilter by rememberSaveable { mutableStateOf(SalesFilter.All) }
        // Cada destino recuerda su estado (pestaña de ventas, scroll) al ir y volver.
        val saveable = rememberSaveableStateHolder()
        val switcher = koinScreenModel<StoreSwitcherScreenModel>()
        val switcherState by switcher.state.collectAsState()
        // Punto rojo de la campana: vencidas, que vencen hoy y documentos rechazados (de Room).
        val notices = koinInject<NoticesRepository>()
        val zone = switcherState.session?.store?.timeZone ?: DEFAULT_TIME_ZONE
        val urgentNotices by remember(idMarket, zone) {
            notices.observe(idMarket, zone).map { list -> list.count { it.urgent } }
        }.collectAsState(0)

        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = tab != MainTab.Invoices,
            onBackCompleted = { tab = MainTab.Invoices },
        )

        Box(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
            Column(Modifier.fillMaxSize()) {
                // La cabecera NO se desliza con el destino: la identidad (logo, usuario,
                // tienda) se queda quieta y solo cambia lo que es del destino.
                MainHeader(
                    tab = tab,
                    session = switcherState.session,
                    onOpenStores = switcher::open,
                    onOpenProfile = { navigator.push(ProfileScreen) },
                    onNewProduct = { navigator.push(ProductEditorScreen()) },
                    onNewClient = { navigator.push(ClientEditorScreen()) },
                    onNewAccount = { navigator.push(BankAccountEditorScreen()) },
                    urgentNotices = urgentNotices,
                    onOpenNotifications = { navigator.push(NotificationsScreen) },
                    onOpenCatalog = { navigator.push(CatalogScreen) },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { tabTransition(forward = targetState.ordinal > initialState.ordinal) },
                        modifier = Modifier.fillMaxSize(),
                        label = "mainTab",
                    ) { current ->
                        saveable.SaveableStateProvider(current.name) {
                            when (current) {
                                MainTab.Invoices -> InvoicesTab(
                                    model = invoicesModel,
                                    onFilterChange = { invoicesFilter = it },
                                    onOpenSale = { navigator.push(SaleDetailScreen(saleId = it)) },
                                )
                                MainTab.Summary -> SummaryTab(model = summaryModel)
                                MainTab.Products -> ProductsTab(
                                    model = productsModel,
                                    onOpenProduct = { navigator.push(ProductDetailScreen(it)) },
                                )
                                MainTab.Clients -> ClientsTab(
                                    model = clientsModel,
                                    onOpenClient = { navigator.push(ClientDetailScreen(it)) },
                                )
                                MainTab.Reports -> ReportsTab(onOpenReport = { navigator.push(ReportScreen(it)) })
                                MainTab.Banks -> BanksTab(
                                    model = banksModel,
                                    onOpenAccount = { navigator.push(BankAccountDetailScreen(it)) },
                                )
                            }
                        }
                    }

                    // Cotización solo desde la pestaña Cotizaciones de Facturas; factura en el resto.
                    val kind = if (tab == MainTab.Invoices && invoicesFilter == SalesFilter.Quotes) {
                        DocumentKind.Quote
                    } else {
                        DocumentKind.Invoice
                    }
                    PbFab(
                        icon = PbSymbols.Add,
                        contentDescription = kind.createLabel,
                        onClick = { navigator.push(InvoiceEditorScreen(kind)) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                            .padding(PbSpace.s6),
                    )
                }
                PbBottomBar(
                    items = MainTab.entries.map { it.item },
                    selectedIndex = tab.ordinal,
                    onSelect = { tab = MainTab.entries[it] },
                )
            }

            StoreSwitcherSheet(state = switcherState, model = switcher)
        }
    }
}

/**
 * Cambio de destino: el nuevo entra desde el lado hacia el que se avanza en la barra y el
 * viejo sale por el otro, recorriendo solo una parte del ancho y fundiéndose. Firme y corto:
 * se lee como pasar de página en un libro de cuentas, no como un carrusel.
 */
private fun tabTransition(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    val slide = { width: Int -> (width * PbMotion.PAGE_SLIDE_FRACTION).roundToInt() * direction }
    val enter = slideInHorizontally(tween(PbMotion.PAGE_MS, easing = PbMotion.ease)) { slide(it) } +
        fadeIn(tween(PbMotion.PAGE_MS, easing = PbMotion.ease))
    // Lo que se va, se va antes: a mitad de camino ya no compite con lo que llega.
    val exit = slideOutHorizontally(tween(PbMotion.PAGE_MS, easing = PbMotion.exit)) { -slide(it) } +
        fadeOut(tween(PbMotion.PAGE_MS / 2, easing = PbMotion.exit))
    return enter togetherWith exit
}

/**
 * Cabecera fija de los destinos: isotipo de PayBille · usuario y tienda (centrados; tocarlos cambia de
 * tienda) · la acción propia del destino y Mi perfil.
 */
@Composable
private fun MainHeader(
    tab: MainTab,
    session: Session?,
    onOpenStores: () -> Unit,
    onOpenProfile: () -> Unit,
    onNewProduct: () -> Unit,
    onNewClient: () -> Unit,
    onNewAccount: () -> Unit,
    urgentNotices: Int,
    onOpenNotifications: () -> Unit,
    onOpenCatalog: () -> Unit,
) {
    PbAppBar(
        modifier = Modifier
            .background(PbTheme.colors.island)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        leading = {
            Image(
                painter = painterResource(Res.drawable.paybille_isotipo),
                contentDescription = "PayBille",
                modifier = Modifier.padding(start = PbSpace.s2).size(32.dp),
            )
        },
        center = {
            if (session != null) {
                StoreSwitcherButton(
                    userName = session.displayName,
                    storeName = session.store?.name ?: "Tienda ${session.idMarket}",
                    onClick = onOpenStores,
                )
            }
        },
        actions = {
            when (tab) {
                MainTab.Invoices -> NotificationsBell(urgent = urgentNotices, onClick = onOpenNotifications)
                MainTab.Products -> {
                    PbIconButton(icon = PbSymbols.Storefront, contentDescription = "Catálogo en línea", onClick = onOpenCatalog)
                    PbIconButton(icon = PbSymbols.AddBox, contentDescription = "Nuevo producto", onClick = onNewProduct)
                }
                MainTab.Clients -> PbIconButton(icon = PbSymbols.PersonAdd, contentDescription = "Nuevo cliente", onClick = onNewClient)
                MainTab.Banks -> PbIconButton(icon = PbSymbols.AddCard, contentDescription = "Nueva cuenta", onClick = onNewAccount)
                MainTab.Summary, MainTab.Reports -> Unit
            }
            PbIconButton(icon = PbSymbols.AccountCircle, contentDescription = "Mi perfil", onClick = onOpenProfile)
        },
    )
}

/**
 * Parte de la cabecera que es DEL destino (buscador, chips), bajo la cabecera fija de
 * [MainScreen], con la línea de 1 dp que cierra la isla. `title` no se pinta (la barra
 * inferior ya nombra el destino), pero el lector de pantalla lo anuncia al cambiar.
 */
@Composable
fun TabHeader(
    title: String,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        Modifier
            .background(PbTheme.colors.island)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .semantics { paneTitle = title },
    ) {
        below()
        Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
    }
}
