package io.vela.core.ui.image

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import io.vela.core.ui.theme.VelaTheme
import java.io.File
import androidx.compose.ui.graphics.FilterQuality

/** Model for app icons: `appicon://<package>`. */
fun appIconModel(packageName: String): String = "appicon://$packageName"

/** Turns a stored artwork reference into something Coil understands. */
fun artworkModel(path: String?): Any? = when {
    path == null -> null
    path.startsWith("content://") || path.startsWith("http") || path.startsWith("appicon://") -> path
    else -> File(path)
}

/** One request per (model, crossfade) pair; rebuilding it on every recomposition made Coil re-check each card. */
@Composable
private fun rememberArtworkRequest(model: Any?): ImageRequest {
    val context = LocalContext.current
    val crossfadeMs = VelaTheme.motion.transitionDurationMs
    return remember(model, crossfadeMs, context) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(crossfadeMs)
            .precision(Precision.INEXACT)
            .build()
    }
}

/**
 * Artwork image with the rules the whole UI follows: decode at display size, crossfade,
 * platform-tinted gradient while loading or when there is no art.
 *
 * Built on [rememberAsyncImagePainter] rather than a subcomposing image: grids show dozens of
 * these at once and subcomposition per card was the single biggest cost while scrolling. The
 * painter is always drawn (it learns its size from the draw scope), the placeholder sits on top
 * until the image arrives.
 */
@Composable
fun VelaImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    accent: Color = VelaTheme.colors.accentSecondary,
    placeholder: (@Composable () -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    colorFilter: ColorFilter? = null,
) {
    val request = rememberArtworkRequest(model)
    val painter = rememberAsyncImagePainter(model = request, contentScale = contentScale)
    val state by painter.state.collectAsState()
    Box(modifier, propagateMinConstraints = true) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            alignment = alignment,
            contentScale = contentScale,
            colorFilter = colorFilter,
        )
        if (state !is AsyncImagePainter.State.Success) {
            if (placeholder != null) placeholder() else ArtPlaceholder(accent)
        }
    }
}

@Composable
fun ArtPlaceholder(accent: Color, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.55f), colors.surfaceElevated, colors.surface),
                ),
            ),
    )
}

/** Coil fetcher resolving `appicon://package` to the launcher icon, rasterised once per size. */
class AppIconFetcher(private val context: Context, private val packageName: String, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val pm = context.packageManager
        val drawable = try {
            pm.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val px = (options.size.width as? coil3.size.Dimension.Pixels)?.px ?: 192
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null && drawable !is AdaptiveIconDrawable) {
            drawable.bitmap
        } else {
            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, px, px)
                drawable.draw(canvas)
            }
        }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = false, dataSource = DataSource.DISK)
    }

    /** Coil maps String models to [coil3.Uri] before fetching, so the factory keys on the URI scheme. */
    class Factory(private val context: Context) : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "appicon") return null
            val packageName = data.authority?.takeIf { it.isNotBlank() } ?: data.path?.trim('/') ?: return null
            return AppIconFetcher(context, packageName, options)
        }
    }
}

@Composable
fun PainterImage(painter: Painter, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    Image(painter = painter, contentDescription = null, modifier = modifier, contentScale = contentScale)
}


/**
 * Box art that is never cropped: the image is fitted inside the card and the empty bands are
 * filled with a soft, dimmed copy of the same art, so landscape boxes (SNES, N64) and
 * portrait boxes (PS1, GBA) share one card size without looking cut.
 */
@Composable
fun FittedArtwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    accent: Color = VelaTheme.colors.accentSecondary,
    placeholder: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val request = rememberArtworkRequest(model)
    val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.Fit)
    val state by painter.state.collectAsState()
    val success = state as? AsyncImagePainter.State.Success
    val portraitish = success?.let { s -> s.painter.intrinsicSize.let { it.height >= it.width * 1.25f } } ?: false
    Box(modifier, propagateMinConstraints = true) {
        if (success != null && !portraitish) {
            // Fill behind with a soft copy: a 40px decode of the same art stretched with bilinear
            // filtering reads as a blur but costs nothing per frame, unlike a RenderEffect blur
            // (which made grids with dozens of cards stutter on mid-range phones).
            val soft = remember(model, context) {
                ImageRequest.Builder(context)
                    .data(model)
                    .size(40)
                    .precision(Precision.EXACT)
                    .scale(Scale.FILL)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = soft,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                alpha = 0.7f,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(VelaTheme.colors.background.copy(alpha = 0.25f)))
        }
        // The loaded painter is drawn directly (no crossfade), as before; until then the async
        // painter draws nothing but tells Coil the size to decode at.
        Image(
            painter = success?.painter ?: painter,
            contentDescription = contentDescription,
            contentScale = if (portraitish) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (success == null) {
            if (placeholder != null) placeholder() else ArtPlaceholder(accent)
        }
    }
}
