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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.crossfade
import coil3.size.Precision
import io.vela.core.ui.theme.VelaTheme
import java.io.File

/** Model for app icons: `appicon://<package>`. */
fun appIconModel(packageName: String): String = "appicon://$packageName"

/** Turns a stored artwork reference into something Coil understands. */
fun artworkModel(path: String?): Any? = when {
    path == null -> null
    path.startsWith("content://") || path.startsWith("http") || path.startsWith("appicon://") -> path
    else -> File(path)
}

/**
 * Artwork image with the rules the whole UI follows: decode at display size, crossfade,
 * platform-tinted gradient while loading or when there is no art.
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
) {
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(model)
        .crossfade(VelaTheme.motion.transitionDurationMs)
        .precision(Precision.INEXACT)
        .build()
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
    ) {
        val state = painter.state.collectAsState().value
        when (state) {
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            else -> if (placeholder != null) placeholder() else ArtPlaceholder(accent)
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
 * filled with a blurred, dimmed copy of the same art, so landscape boxes (SNES, N64) and
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
    val request = ImageRequest.Builder(context)
        .data(model)
        .crossfade(VelaTheme.motion.transitionDurationMs)
        .precision(Precision.INEXACT)
        .build()
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    ) {
        val state = painter.state.collectAsState().value
        if (state is AsyncImagePainter.State.Success) {
            val intrinsic = state.painter.intrinsicSize
            val portraitish = intrinsic.height >= intrinsic.width * 1.25f
            Box(Modifier.fillMaxSize()) {
                if (!portraitish) {
                    // Fill behind with a soft copy, then the real art fitted on top.
                    Image(
                        painter = state.painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(22.dp)
                            .alpha(0.7f),
                    )
                    Box(Modifier.fillMaxSize().background(VelaTheme.colors.background.copy(alpha = 0.25f)))
                }
                Image(
                    painter = state.painter,
                    contentDescription = contentDescription,
                    contentScale = if (portraitish) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (placeholder != null) {
            placeholder()
        } else {
            ArtPlaceholder(accent)
        }
    }
}
