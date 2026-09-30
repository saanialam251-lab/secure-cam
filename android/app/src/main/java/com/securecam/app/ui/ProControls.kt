package com.securecam.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.securecam.app.ui.theme.Gray
import com.securecam.app.ui.theme.GrayLight
import com.securecam.app.ui.theme.Ink
import com.securecam.app.ui.theme.InkElevated
import com.securecam.app.ui.theme.Paper
import com.securecam.app.ui.theme.RecordRed
import com.securecam.app.ui.theme.Divider
import com.securecam.app.viewmodel.CameraUiState
import com.securecam.app.viewmodel.CameraViewModel
import com.securecam.app.viewmodel.WhiteBalanceMode
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Pro ("DSLR") mode panel — manual ISO, shutter, white balance, focus
 * distance and EV, plus a live luminance histogram.
 *
 * Minimalism styling: ink-elevated panel, hairline dividers, small-caps
 * axis labels with the value right-aligned on the same row, paper sliders.
 */
object ProControls {

    // ─────────────────────────────────────────────────────────────────────
    // Histogram
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 64-bin luminance histogram. Paper bars on translucent ink; a hairline
     * at the 50 % mark aids orientation without cluttering the viewfinder.
     */
    @Composable
    fun HistogramChart(
        bins: IntArray,
        modifier: Modifier = Modifier,
    ) {
        Canvas(modifier.clip(RoundedCornerShape(6.dp))) {
            // Panel backdrop.
            drawRect(Ink.copy(alpha = 0.55f))

            val binWidth = size.width / bins.size
            val maxCount = (bins.maxOrNull() ?: 1).coerceAtLeast(1)

            bins.forEachIndexed { index, count ->
                val barHeight = (count.toFloat() / maxCount) * size.height
                if (barHeight > 0f) {
                    drawRect(
                        color = Paper.copy(alpha = 0.85f),
                        topLeft = Offset(index * binWidth, size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(binWidth * 0.8f, barHeight),
                    )
                }
            }

            // Center hairline (mid-gray).
            drawLine(
                color = Gray.copy(alpha = 0.9f),
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1f,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Panel
    // ─────────────────────────────────────────────────────────────────────

    @Composable
    fun Panel(
        viewModel: CameraViewModel,
        uiState: CameraUiState,
        modifier: Modifier = Modifier,
    ) {
        val caps = uiState.proCapabilities
        val settings = uiState.proSettings

        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(InkElevated.copy(alpha = 0.94f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            PanelHeader(viewModel, uiState)
            Hairline()

            // ── ISO ─────────────────────────────────────────────────────
            AxisRow(
                label = "ISO",
                value = settings.iso?.toString() ?: "Auto",
                onReset = { viewModel.setManualIso(null) },
            )
            Slider(
                value = isoToFraction(settings.iso, caps.isoRange.first, caps.isoRange.last),
                onValueChange = { fraction ->
                    viewModel.setManualIso(fractionToIso(fraction, caps.isoRange))
                },
                colors = sliderColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Hairline()

            // ── Shutter (log-scaled) ────────────────────────────────────
            AxisRow(
                label = "SHUTTER",
                value = settings.exposureTimeNanos?.let { formatShutter(it) } ?: "Auto",
                onReset = { viewModel.setManualExposureTime(null) },
            )
            Slider(
                value = nanosToFraction(
                    settings.exposureTimeNanos,
                    caps.exposureRangeNanos.first,
                    caps.exposureRangeNanos.last,
                ),
                onValueChange = { fraction ->
                    viewModel.setManualExposureTime(
                        fractionToNanos(fraction, caps.exposureRangeNanos),
                    )
                },
                colors = sliderColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Hairline()

            // ── White balance ───────────────────────────────────────────
            Text(
                text = "WHITE BALANCE",
                style = MaterialTheme.typography.labelSmall,
                color = GrayLight,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WbChip("AUTO", settings.wbKelvin == null) { viewModel.setManualWhiteBalance(null) }
                WbChip("DAY", settings.wbKelvin == WhiteBalanceMode.DAYLIGHT.kelvin) {
                    viewModel.setManualWhiteBalance(WhiteBalanceMode.DAYLIGHT.kelvin)
                }
                WbChip("CLOUD", settings.wbKelvin == WhiteBalanceMode.CLOUDY.kelvin) {
                    viewModel.setManualWhiteBalance(WhiteBalanceMode.CLOUDY.kelvin)
                }
                WbChip("SHADE", settings.wbKelvin == WhiteBalanceMode.SHADE.kelvin) {
                    viewModel.setManualWhiteBalance(WhiteBalanceMode.SHADE.kelvin)
                }
                WbChip("TUNG", settings.wbKelvin == WhiteBalanceMode.TUNGSTEN.kelvin) {
                    viewModel.setManualWhiteBalance(WhiteBalanceMode.TUNGSTEN.kelvin)
                }
            }
            if (settings.wbKelvin != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${settings.wbKelvin} K",
                    style = MaterialTheme.typography.labelMedium,
                    color = Paper,
                )
            }

            Hairline()

            // ── Focus distance ──────────────────────────────────────────
            AxisRow(
                label = "FOCUS",
                value = settings.focusDistanceDiopters
                    ?.let { String.format("%.1f dpt", it) }
                    ?: "Auto (AF)",
                onReset = { viewModel.setManualFocusDistance(null) },
            )
            Slider(
                value = settings.focusDistanceDiopters
                    ?.div(caps.maxFocusDiopters.coerceAtLeast(0.1f))
                    ?: 0f,
                onValueChange = { fraction ->
                    viewModel.setManualFocusDistance(
                        if (fraction <= 0.01f) null else fraction * caps.maxFocusDiopters,
                    )
                },
                colors = sliderColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Hairline()

            // ── EV compensation ─────────────────────────────────────────
            AxisRow(
                label = "EV",
                value = if (settings.evIndex == 0) "0.0" else {
                    val sign = if (settings.evIndex > 0) "+" else ""
                    String.format("%s%.1f", sign, settings.evIndex * caps.evStep)
                },
                onReset = { viewModel.setExposureCompensation(0) },
            )
            Slider(
                value = ((settings.evIndex - caps.evRange.first).toFloat() /
                    (caps.evRange.last - caps.evRange.first).coerceAtLeast(1)),
                onValueChange = { fraction ->
                    val span = caps.evRange.last - caps.evRange.first
                    val idx = caps.evRange.first + (fraction * span).roundToInt()
                    viewModel.setExposureCompensation(idx)
                },
                colors = sliderColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Hairline()

            // ── RAW toggle (only when the sensor delivers DNG) ──────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "RAW (DNG)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Paper,
                    )
                    Text(
                        if (caps.rawSupported) "Uncompressed sensor capture" else "Not supported on this lens",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (caps.rawSupported) Gray else RecordRed.copy(alpha = 0.9f),
                    )
                }
                Switch(
                    checked = uiState.rawEnabled,
                    onCheckedChange = { viewModel.setRawEnabled(it) },
                    enabled = caps.rawSupported,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Paper,
                        checkedThumbColor = Ink,
                        uncheckedTrackColor = Gray.copy(alpha = 0.3f),
                        uncheckedThumbColor = Paper,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pieces
    // ─────────────────────────────────────────────────────────────────────

    @Composable
    private fun PanelHeader(viewModel: CameraViewModel, uiState: CameraUiState) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "PRO",
                    style = MaterialTheme.typography.labelLarge,
                    color = Paper,
                )
                Text(
                    if (uiState.proCapabilities.proSupported) {
                        "Manual sensor controls"
                    } else {
                        "Manual controls limited on this device"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray,
                )
            }
            Text(
                "RESET",
                style = MaterialTheme.typography.labelSmall,
                color = GrayLight,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { viewModel.resetProSettings() }
                    .padding(6.dp),
            )
        }
    }

    /** Axis label left, live value right, tap the value to return to auto. */
    @Composable
    private fun AxisRow(label: String, value: String, onReset: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = GrayLight,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = Paper,
                modifier = Modifier.clickable(onClick = onReset),
            )
        }
    }

    @Composable
    private fun WbChip(label: String, selected: Boolean, onClick: () -> Unit) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Ink else Paper,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) Paper else Paper.copy(alpha = 0.12f))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }

    @Composable
    private fun Hairline() {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider.copy(alpha = 0.25f)),
        ) {}
        Spacer(Modifier.height(10.dp))
    }

    private fun sliderColors() = SliderDefaults.colors(
        thumbColor = Paper,
        activeTrackColor = Paper.copy(alpha = 0.9f),
        inactiveTrackColor = Paper.copy(alpha = 0.2f),
    )

    // ─────────────────────────────────────────────────────────────────────
    // Value mapping helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun isoToFraction(iso: Int?, min: Int, max: Int): Float {
        if (iso == null || max <= min) return 0f
        return (iso - min).toFloat() / (max - min)
    }

    private fun fractionToIso(fraction: Float, range: IntRange): Int? {
        if (fraction <= 0.01f) return null // left edge = Auto
        val span = (range.last - range.first).coerceAtLeast(1)
        return range.first + (fraction * span).roundToInt()
    }

    /** Logarithmic mapping so short shutter speeds get usable slider space. */
    private fun nanosToFraction(nanos: Long?, min: Long, max: Long): Float {
        if (nanos == null || max <= min) return 0f
        val lnMin = ln(min.toDouble())
        val lnMax = ln(max.toDouble())
        return ((ln(nanos.toDouble()) - lnMin) / (lnMax - lnMin)).toFloat().coerceIn(0f, 1f)
    }

    private fun fractionToNanos(fraction: Float, range: LongRange): Long? {
        if (fraction <= 0.01f) return null // left edge = Auto
        val lnMin = ln(range.first.toDouble())
        val lnMax = ln(range.last.toDouble())
        val value = exp(lnMin + fraction * (lnMax - lnMin))
        return value.toLong().coerceIn(range.first, range.last)
    }

    /** "1/500 s" below a second, "1.0 s" at and above. */
    fun formatShutter(nanos: Long): String =
        if (nanos < 1_000_000_000L) {
            "1/${(1e9 / nanos).roundToInt()} s"
        } else {
            String.format("%.1f s", nanos / 1e9)
        }
}
