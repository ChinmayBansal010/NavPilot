package com.navpilot.presentation.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.navpilot.core.ui.theme.Ink2
import com.navpilot.core.ui.theme.Surface
import com.navpilot.core.ui.theme.Warning
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint

@Composable
fun OfflineMap(
    showDashedRoute: Boolean = false
) {

    val context = LocalContext.current

    val map = rememberMap(context)

    DisposableEffect(map) {

        onDispose {
            map.onDetach()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        AndroidView(
            factory = {
                map
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFF2563EB))
                .padding(3.dp)
                .clip(CircleShape)
                .background(Color.White)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Surface.copy(alpha = .94f))
                .padding(
                    horizontal = 10.dp,
                    vertical = 6.dp
                )
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        if (showDashedRoute)
                            Icons.Default.Download
                        else
                            Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint =
                        if (showDashedRoute)
                            Warning
                        else
                            Ink2
                )

                Spacer(
                    modifier = Modifier.size(5.dp)
                )

                Text(
                    text =
                        if (showDashedRoute)
                            "Offline maps"
                        else
                            "Live maps",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink2
                )
            }
        }
    }
}

private fun rememberMap(
    context: Context
): MapView {

    Configuration.getInstance().load(
        context,
        context.getSharedPreferences(
            "osmdroid",
            Context.MODE_PRIVATE
        )
    )

    return MapView(context).apply {

        setTileSource(
            TileSourceFactory.MAPNIK
        )

        setMultiTouchControls(true)

        controller.setZoom(14.5)

        controller.setCenter(
            GeoPoint(
                28.4595,
                77.0266
            )
        )

        isTilesScaledToDpi = true
    }
}