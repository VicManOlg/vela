package io.vela.core.common

import kotlinx.serialization.json.Json

/** Shared lenient JSON configuration for catalogs, themes and user files. */
val VelaJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = true
    coerceInputValues = true
    allowTrailingComma = true
    allowComments = true
}
