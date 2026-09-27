package com.paybille.invoicer.feature.auth.data

import com.paybille.invoicer.feature.auth.data.local.MarketEntity
import com.paybille.invoicer.feature.auth.data.local.SessionEntity
import com.paybille.invoicer.feature.auth.data.local.SettingsEntity
import com.paybille.invoicer.feature.auth.data.remote.MarketDto
import com.paybille.invoicer.feature.auth.data.remote.MarketOptionDto
import com.paybille.invoicer.feature.auth.data.remote.SettingsDto
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.auth.domain.StoreOption
import com.paybille.invoicer.feature.auth.domain.StoreProfile
import com.paybille.invoicer.feature.auth.domain.StoreSettings

private const val DEFAULT_TAX_LABEL = "ITBIS"
private const val DEFAULT_TIME_ZONE = "America/Santo_Domingo"

internal fun MarketDto.toEntity() = MarketEntity(
    id = id,
    name = name.trim(),
    address = address,
    phone = phone,
    rnc = rnc,
    image = image,
    taxValue = taxValue,
    taxLabel = taxLabel,
    taxType = taxType,
    timeZone = timeZone,
)

internal fun SettingsDto.toEntity(idMarket: Int) = SettingsEntity(
    idMarket = idMarket,
    remoteId = id,
    tax = tax,
    logoInBill = logoInBill,
)

internal fun MarketOptionDto.toOption() = StoreOption(
    id = id,
    name = name.trim().ifBlank { "Tienda $id" },
    address = address?.trim()?.takeIf { it.isNotEmpty() },
)

internal fun SessionEntity.toDomain(market: MarketEntity?, settings: SettingsEntity?) = Session(
    userId = userId,
    username = username,
    idPerson = idPerson,
    idMarket = idMarket,
    torning = torning,
    firstName = firstName,
    lastName = lastName,
    roleName = roleName,
    store = market?.toDomain(),
    settings = settings?.let { StoreSettings(tax = it.tax, logoInBill = it.logoInBill == true) },
    loggedInAt = loggedInAt,
    profileSyncedAt = profileSyncedAt,
)

private fun MarketEntity.toDomain() = StoreProfile(
    id = id,
    name = name,
    address = address,
    phone = phone,
    rnc = rnc,
    image = image,
    taxValue = taxValue,
    taxLabel = taxLabel?.takeIf { it.isNotBlank() } ?: DEFAULT_TAX_LABEL,
    taxType = taxType,
    timeZone = timeZone?.takeIf { it.isNotBlank() } ?: DEFAULT_TIME_ZONE,
)
