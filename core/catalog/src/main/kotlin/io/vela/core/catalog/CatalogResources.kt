package io.vela.core.catalog

import io.vela.core.common.VelaJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/** Reads bundled JSON catalogs from the module's classpath resources. */
internal object CatalogResources {
    fun readText(path: String): String =
        checkNotNull(CatalogResources::class.java.classLoader.getResourceAsStream(path)) { "Missing catalog resource $path" }
            .bufferedReader()
            .use { it.readText() }

    fun <T> readList(path: String, serializer: KSerializer<T>): List<T> =
        VelaJson.decodeFromString(ListSerializer(serializer), readText(path))

    fun <T> read(path: String, serializer: KSerializer<T>): T =
        VelaJson.decodeFromString(serializer, readText(path))
}
