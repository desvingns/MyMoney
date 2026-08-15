package com.kshavrin.mymoney.core.network.shared

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun JsonElement?.isEntitlementRequiredPayload(): Boolean =
    when (this) {
        is JsonPrimitive -> content == ENTITLEMENT_REQUIRED
        is JsonObject ->
            entries.any { (key, value) ->
                key in ENTITLEMENT_ERROR_KEYS && value.isEntitlementRequiredPayload()
            }

        is JsonArray -> any { it.isEntitlementRequiredPayload() }
        else -> false
    }

private const val ENTITLEMENT_REQUIRED = "entitlement_required"
private val ENTITLEMENT_ERROR_KEYS = setOf("message", "code", "reason", "error", "response")
