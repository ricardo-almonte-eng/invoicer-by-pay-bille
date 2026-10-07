package com.paybille.invoicer.core.di

import com.paybille.invoicer.core.database.InvoicerDatabase
import com.paybille.invoicer.core.database.PayloadCache
import com.paybille.invoicer.core.database.UserDataCleaner
import com.paybille.invoicer.core.database.buildInvoicerDatabase
import com.paybille.invoicer.core.network.ApiConfig
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.TokenProvider
import com.paybille.invoicer.core.network.RemoteImageLoader
import com.paybille.invoicer.core.network.createHttpClient
import com.paybille.invoicer.core.network.createPlainHttpClient
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.data.SessionTokenStore
import com.paybille.invoicer.feature.auth.data.remote.AuthRemoteDataSource
import com.paybille.invoicer.feature.auth.presentation.LoginScreenModel
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.feature.banks.data.BanksRemoteDataSource
import com.paybille.invoicer.feature.banks.data.BanksRepository
import com.paybille.invoicer.feature.banks.presentation.BankAccountDetailScreenModel
import com.paybille.invoicer.feature.banks.presentation.BankAccountEditorScreenModel
import com.paybille.invoicer.feature.banks.presentation.BanksScreenModel
import com.paybille.invoicer.feature.clients.data.ClientsRepository
import com.paybille.invoicer.feature.clients.data.remote.ClientsRemoteDataSource
import com.paybille.invoicer.feature.clients.presentation.ClientDetailScreenModel
import com.paybille.invoicer.feature.clients.presentation.ClientEditorScreenModel
import com.paybille.invoicer.feature.clients.presentation.ClientsScreenModel
import com.paybille.invoicer.feature.detail.data.InvoicePdfStore
import com.paybille.invoicer.feature.document.data.InvoiceDocumentRemote
import com.paybille.invoicer.feature.document.data.InvoiceDocumentRepository
import com.paybille.invoicer.feature.document.presentation.InvoiceSettingsScreenModel
import com.paybille.invoicer.feature.detail.data.ReceivablesRepository
import com.paybille.invoicer.feature.detail.data.SaleDetailRepository
import com.paybille.invoicer.feature.detail.data.remote.DetailRemoteDataSource
import com.paybille.invoicer.feature.detail.presentation.DetailTarget
import com.paybille.invoicer.feature.detail.presentation.SaleDetailScreenModel
import com.paybille.invoicer.feature.invoices.InvoicesScreenModel
import com.paybille.invoicer.feature.catalog.presentation.CatalogScreenModel
import com.paybille.invoicer.feature.invoice.data.CachedPayToMemory
import com.paybille.invoicer.feature.notifications.data.NoticesRepository
import com.paybille.invoicer.feature.notifications.presentation.NotificationsScreenModel
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceSender
import com.paybille.invoicer.feature.invoice.data.remote.CatalogRemoteDataSource
import com.paybille.invoicer.feature.invoice.data.remote.InvoiceRemoteDataSource
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.presentation.ClientPickerScreenModel
import com.paybille.invoicer.feature.invoice.presentation.InvoiceEditorScreenModel
import com.paybille.invoicer.feature.invoice.presentation.ProductPickerScreenModel
import com.paybille.invoicer.feature.products.data.ProductsRepository
import com.paybille.invoicer.feature.products.data.remote.ProductsRemoteDataSource
import com.paybille.invoicer.feature.products.presentation.ProductDetailScreenModel
import com.paybille.invoicer.feature.products.presentation.ProductEditorScreenModel
import com.paybille.invoicer.feature.products.presentation.ProductsScreenModel
import com.paybille.invoicer.feature.profile.ProfileScreenModel
import com.paybille.invoicer.feature.main.StoreSwitcherScreenModel
import com.paybille.invoicer.feature.store.data.StoreSettingsRepository
import com.paybille.invoicer.feature.store.data.remote.StoreRemoteDataSource
import com.paybille.invoicer.feature.store.presentation.StoreSettingsScreenModel
import com.paybille.invoicer.feature.reports.data.ReportsRemoteDataSource
import com.paybille.invoicer.feature.reports.data.ReportsRepository
import com.paybille.invoicer.feature.reports.presentation.ReportKind
import com.paybille.invoicer.feature.reports.presentation.ReportScreenModel
import com.paybille.invoicer.feature.sales.data.SalesRepository
import com.paybille.invoicer.feature.sales.data.remote.SalesRemoteDataSource
import com.paybille.invoicer.feature.summary.SummaryScreenModel
import com.paybille.invoicer.feature.summary.data.SummaryRemoteDataSource
import com.paybille.invoicer.feature.summary.data.SummaryRepository
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Lo que cada plataforma aporta (hoy: dónde vive el archivo de la base local).
 * Android lo resuelve con el `Context` que registra `androidContext()`.
 */
expect val platformModule: Module

private val coreModule = module {
    single { ApiConfig.fromBuild() }
    single { buildInvoicerDatabase(get()) }
    single { get<InvoicerDatabase>().sessionDao() }
    single { get<InvoicerDatabase>().salesDao() }
    single { get<InvoicerDatabase>().invoiceDao() }
    single { get<InvoicerDatabase>().detailDao() }
    single { get<InvoicerDatabase>().payloadDao() }
    singleOf(::PayloadCache)
    single { get<InvoicerDatabase>().productDao() }
    single { get<InvoicerDatabase>().clientDao() }
    single { get<InvoicerDatabase>().bankDao() }
    singleOf(::UserDataCleaner)
    single { createHttpClient(get()) }
    // Cliente aparte, SIN token: las imágenes pueden estar en otro servidor.
    single { RemoteImageLoader(createPlainHttpClient()) }
    singleOf(::PayBilleApi)
}

private val authModule = module {
    singleOf(::SessionTokenStore) bind TokenProvider::class
    singleOf(::AuthRemoteDataSource)
    single { SessionRepository(dao = get(), remote = get(), tokens = get(), cleaner = get()) }
    factoryOf(::LoginScreenModel)
}

private val salesModule = module {
    singleOf(::SalesRemoteDataSource)
    single { SalesRepository(dao = get(), remote = get()) }
}

private val invoiceModule = module {
    singleOf(::CatalogRemoteDataSource)
    singleOf(::InvoiceRemoteDataSource)
    single { InvoiceRepository(dao = get(), payToMemory = CachedPayToMemory(get())) }
    single {
        val sessions = get<SessionRepository>()
        val documents = get<InvoiceDocumentRepository>()
        val receivables = get<ReceivablesRepository>()
        InvoiceSender(
            dao = get(),
            remote = get(),
            sales = get(),
            currentSession = { sessions.currentSession() },
            afterSent = { saleId, onCredit ->
                // Los datos de la factura quedan en el teléfono: se ve al instante y sin red.
                sessions.currentSession()?.let { session ->
                    runCatching { documents.refresh(session.idMarket, saleId) }
                    if (onCredit) runCatching { receivables.refresh(session.idMarket) }
                }
            },
        )
    }
    factory { (kind: DocumentKind) ->
        InvoiceEditorScreenModel(kind = kind, sessions = get(), invoices = get(), catalog = get(), sender = get(), banks = get())
    }
    factory { (kind: DocumentKind) -> ProductPickerScreenModel(kind = kind, sessions = get(), invoices = get(), catalog = get()) }
    factory { (kind: DocumentKind) -> ClientPickerScreenModel(kind = kind, sessions = get(), invoices = get(), catalog = get()) }
}

private val detailModule = module {
    singleOf(::DetailRemoteDataSource)
    singleOf(::InvoicePdfStore)
    singleOf(::InvoiceDocumentRemote)
    singleOf(::InvoiceDocumentRepository)
    factoryOf(::InvoiceSettingsScreenModel)
    single {
        val sessions = get<SessionRepository>()
        ReceivablesRepository(
            dao = get(),
            remote = get(),
            scheduler = get(),
            timeZone = { sessions.currentSession()?.store?.timeZone ?: DEFAULT_TIME_ZONE },
        )
    }
    single { SaleDetailRepository(dao = get(), remote = get(), sales = get(), receivables = get()) }
    factory { (target: DetailTarget) ->
        SaleDetailScreenModel(
            target = target,
            sessions = get(),
            details = get(),
            pdfs = get(),
            documents = get(),
            platform = get(),
            invoices = get(),
            sender = get(),
            catalog = get(),
        )
    }
}

private val homeModule = module {
    factoryOf(::InvoicesScreenModel)
    factoryOf(::ProfileScreenModel)
    factoryOf(::StoreSwitcherScreenModel)
    singleOf(::StoreRemoteDataSource)
    singleOf(::StoreSettingsRepository)
    factoryOf(::StoreSettingsScreenModel)
}

private val summaryModule = module {
    singleOf(::SummaryRemoteDataSource)
    singleOf(::SummaryRepository)
    factoryOf(::SummaryScreenModel)
}

private val catalogModule = module {
    singleOf(::ClientsRemoteDataSource)
    single { ClientsRepository(dao = get(), remote = get(), cache = get()) }
    factoryOf(::ClientsScreenModel)
    factory { (id: Int) -> ClientDetailScreenModel(id = id, sessions = get(), repository = get(), invoices = get()) }
    factory { (id: Int?, pickFor: DocumentKind?) ->
        ClientEditorScreenModel(id = id, pickFor = pickFor, sessions = get(), repository = get(), invoices = get())
    }

    singleOf(::ProductsRemoteDataSource)
    single { ProductsRepository(dao = get(), remote = get(), cache = get()) }
    factoryOf(::ProductsScreenModel)
    factory { (idProduct: Int) -> ProductDetailScreenModel(idProduct = idProduct, sessions = get(), repository = get()) }
    factory { (idProduct: Int?, pickFor: DocumentKind?) ->
        ProductEditorScreenModel(idProduct = idProduct, pickFor = pickFor, sessions = get(), repository = get(), invoices = get())
    }

    factoryOf(::CatalogScreenModel)
    single { NoticesRepository(dao = get(), invoices = get()) }
    factoryOf(::NotificationsScreenModel)
    singleOf(::BanksRemoteDataSource)
    single { BanksRepository(dao = get(), remote = get()) }
    factoryOf(::BanksScreenModel)
    factory { (id: Int) -> BankAccountDetailScreenModel(id = id, sessions = get(), repository = get()) }
    factory { (id: Int?) -> BankAccountEditorScreenModel(id = id, sessions = get(), repository = get()) }

    singleOf(::ReportsRemoteDataSource)
    singleOf(::ReportsRepository)
    factory { (kind: ReportKind) -> ReportScreenModel(kind = kind, sessions = get(), reports = get(), clients = get()) }
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication = startKoin {
    appDeclaration()
    modules(platformModule, coreModule, authModule, salesModule, invoiceModule, detailModule, homeModule, summaryModule, catalogModule)
}
