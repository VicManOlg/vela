package io.vela.core.launcher

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.model.ExtraType
import io.vela.core.model.Game
import io.vela.core.model.GameLocation
import io.vela.core.model.PlatformSettings
import io.vela.core.model.ResolvedPlayer
import io.vela.core.model.RomDelivery
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Everything an intent template can reference. */
data class LaunchContext(
    val romUri: Uri?,
    val romPath: String?,
    val romName: String,
    val romStem: String,
    val romDir: String?,
    val corePath: String?,
    val coreId: String?,
    val packageName: String,
    val platformId: String,
    val overrides: Map<String, String>,
) {
    fun variables(): Map<String, String> = buildMap {
        put("rom.uri", romUri?.toString().orEmpty())
        put("rom.path", romPath.orEmpty())
        put("rom.name", romName)
        put("rom.stem", romStem)
        put("rom.dir", romDir.orEmpty())
        put("core.path", corePath.orEmpty())
        put("core.id", coreId.orEmpty())
        put("package", packageName)
        put("platform.id", platformId)
        putAll(overrides)
    }
}

/** Result of template expansion, ready for `startActivity`. */
data class PreparedLaunch(
    val intent: Intent,
    val targetPackage: String,
    /** Content URIs the target package must be able to read. */
    val urisToGrant: List<Uri>,
)

/**
 * Turns a [io.vela.core.model.PlayerDefinition] template into a concrete [Intent]. Pure data in,
 * intent out; nothing here knows about specific emulators.
 */
@Singleton
class LaunchIntentBuilder @Inject constructor(@ApplicationContext private val context: Context) {

    private val providerAuthority: String get() = "${context.packageName}.files"

    fun build(game: Game, resolved: ResolvedPlayer, settings: PlatformSettings?): PreparedLaunch {
        val def = resolved.definition
        val pkg = resolved.installedPackage
        val ctx = launchContext(game, resolved, settings)
        val vars = ctx.variables()

        val intent = Intent()
        def.action?.let { intent.action = it.expand(vars) }
        def.categories.forEach { intent.addCategory(it.expand(vars)) }

        val activity = def.activity?.expand(vars)
        if (activity != null) {
            val className = if (activity.startsWith(".")) pkg + activity else activity
            intent.component = ComponentName(pkg, className)
        } else {
            intent.setPackage(pkg)
        }

        val grants = mutableListOf<Uri>()
        when (def.delivery) {
            RomDelivery.CONTENT_URI_DATA -> ctx.romUri?.let { uri ->
                if (def.mimeType != null) intent.setDataAndType(uri, def.mimeType) else intent.data = uri
                grants += uri
            }
            RomDelivery.FILE_URI_DATA -> ctx.romPath?.let { path ->
                val uri = Uri.fromFile(File(path))
                if (def.mimeType != null) intent.setDataAndType(uri, def.mimeType) else intent.data = uri
            }
            RomDelivery.PATH_DATA -> ctx.romPath?.let { path ->
                val uri = Uri.parse(path)
                if (def.mimeType != null) intent.setDataAndType(uri, def.mimeType) else intent.data = uri
            }
            RomDelivery.EXTRAS_ONLY, RomDelivery.NONE -> Unit
        }

        for (extra in def.extras) {
            val value = extra.value.expand(vars)
            if (value.isEmpty() && !extra.allowEmpty) continue
            when (extra.type) {
                ExtraType.STRING -> intent.putExtra(extra.key, value)
                ExtraType.BOOLEAN -> intent.putExtra(extra.key, value.equals("true", ignoreCase = true))
                ExtraType.INT -> value.toIntOrNull()?.let { intent.putExtra(extra.key, it) }
                ExtraType.LONG -> value.toLongOrNull()?.let { intent.putExtra(extra.key, it) }
                ExtraType.FLOAT -> value.toFloatOrNull()?.let { intent.putExtra(extra.key, it) }
                ExtraType.URI -> intent.putExtra(extra.key, Uri.parse(value))
                ExtraType.STRING_ARRAY -> intent.putExtra(extra.key, value.split(',').map(String::trim).toTypedArray())
            }
            if (value.startsWith("content://")) grants += Uri.parse(value)
        }

        var flags = 0
        for (name in def.flags) flags = flags or flagValue(name)
        if (grants.isNotEmpty()) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.addFlags(flags)

        // Content URIs in extras receive no automatic grant; clipData extends the grant to them.
        if (grants.isNotEmpty()) {
            val clip = ClipData.newRawUri("rom", grants.first())
            grants.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            intent.clipData = clip
        }

        Timber.d("Prepared launch %s -> %s", game.displayTitle, intent.toUri(0))
        return PreparedLaunch(intent, pkg, grants.distinct())
    }

    fun launchContext(game: Game, resolved: ResolvedPlayer, settings: PlatformSettings?): LaunchContext {
        val pkg = resolved.installedPackage
        val (uri, path) = when (val loc = game.location) {
            is GameLocation.File -> providerUri(File(loc.path)) to loc.path
            is GameLocation.Document -> Uri.parse(loc.uri) to documentPath(loc.uri)
            is GameLocation.AndroidApp -> null to null
            is GameLocation.External -> null to loc.target
        }
        val core = resolved.core
        val coreVars = mapOf("package" to pkg, "core.file" to core?.fileName.orEmpty(), "core.id" to core?.id.orEmpty())
        val corePath = if (core != null) resolved.definition.corePathTemplate?.expand(coreVars) else null
        return LaunchContext(
            romUri = uri,
            romPath = path,
            romName = game.fileName,
            romStem = game.fileName.substringBeforeLast('.'),
            romDir = path?.substringBeforeLast('/', ""),
            corePath = corePath,
            coreId = core?.id,
            packageName = pkg,
            platformId = game.platformId.value,
            overrides = settings?.launchOverrides.orEmpty(),
        )
    }

    private fun providerUri(file: File): Uri? = try {
        FileProvider.getUriForFile(context, providerAuthority, file)
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "FileProvider cannot share %s", file)
        null
    }

    /**
     * Best-effort real path for an external-storage SAF document so path-only emulators still work:
     * `content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fsnes%2Fx.sfc`
     * -> `/storage/emulated/0/ROMs/snes/x.sfc`.
     */
    fun documentPath(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val volume = docId.substringBefore(':')
        val relative = docId.substringAfter(':', "")
        val root = when (volume) {
            "primary" -> Environment.getExternalStorageDirectory().absolutePath
            "home" -> "${Environment.getExternalStorageDirectory().absolutePath}/Documents"
            else -> "/storage/$volume"
        }
        return if (relative.isEmpty()) root else "$root/$relative"
    }

    private fun flagValue(name: String): Int = try {
        Intent::class.java.getField(name).getInt(null)
    } catch (e: Exception) {
        Timber.w("Unknown intent flag %s", name)
        0
    }

    private fun String.expand(vars: Map<String, String>): String {
        if (!contains('{')) return this
        var out = this
        for ((k, v) in vars) out = out.replace("{$k}", v)
        return out
    }
}
