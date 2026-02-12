package org.pixel.customparts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var currentColor by remember { mutableStateOf(Color(initialColor)) }
    
    
    val hsv = remember {
        val hsvArray = FloatArray(3)
        AndroidColor.colorToHSV(initialColor, hsvArray)
        mutableStateListOf(hsvArray[0], hsvArray[1], hsvArray[2])
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Pick a Color",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ColorWheel(
                    hue = hsv[0],
                    saturation = hsv[1],
                    value = hsv[2],
                    onColorChange = { h, s, v ->
                        hsv[0] = h
                        hsv[1] = s
                        hsv[2] = v
                        currentColor = Color.hsv(h, s, v)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                
                Text("Brightness", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = hsv[2],
                    onValueChange = {
                        hsv[2] = it
                        currentColor = Color.hsv(hsv[0], hsv[1], it)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onColorSelected(currentColor.toArgb()) }) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onColorChange: (Float, Float, Float) -> Unit
) {
    
    
    
    
    BoxWithConstraints(
        modifier = Modifier.size(250.dp),
        contentAlignment = Alignment.Center
    ) {
        val radius = maxWidth.value / 2
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val radiusPx = radius * density

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            updateColorFromInput(offset, radiusPx, value, onColorChange)
                        },
                        onDrag = { change, _ ->
                            updateColorFromInput(change.position, radiusPx, value, onColorChange)
                        }
                    )
                }
        ) {
            
            val hueColors = listOf(
                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
            drawCircle(
                brush = Brush.sweepGradient(hueColors, center = center)
            )
            
            
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = radiusPx
                )
            )
            
            
            
            
        }

        
        val angle = Math.toRadians(hue.toDouble())
        val dist = saturation * radiusPx
        val knobX = (cos(angle) * dist).toFloat()
        val knobY = (sin(angle) * dist).toFloat()

        Box(
            modifier = Modifier
                .offset { IntOffset(knobX.roundToInt(), knobY.roundToInt()) }
                .size(24.dp)
                .background(Color.White, CircleShape)
                .border(2.dp, Color.Black, CircleShape)
        )
    }
}

private fun updateColorFromInput(
    position: Offset,
    radius: Float,
    currentValue: Float,
    onColorChange: (Float, Float, Float) -> Unit
) {
    val centerX = radius
    val centerY = radius
    val dx = position.x - centerX
    val dy = position.y - centerY
    
    val angleRad = atan2(dy, dx)
    var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
    if (angleDeg < 0) angleDeg += 360f
    
    val dist = hypot(dx, dy)
    val saturation = (dist / radius).coerceIn(0.0f, 1.0f)
    
    onColorChange(angleDeg, saturation, currentValue)
}
