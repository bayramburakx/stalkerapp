package com.stalkerapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape
import com.stalkerapp.ui.tv.isTvDevice
import com.stalkerapp.ui.tv.isTvSelectKey

/**
 * Portio Girdi Bileşenleri (Inputs)
 */

@Composable
fun portioTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.4f),
        focusedContainerColor = Color.Black.copy(alpha = 0.5f),
        unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
        disabledContainerColor = Color.Transparent,
        cursorColor = Color.White,
        focusedBorderColor = Color.White,
        unfocusedBorderColor = PortioColors.HairlineStrong,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
        focusedLeadingIconColor = Color.White,
        unfocusedLeadingIconColor = Color.White.copy(alpha = 0.6f),
        focusedTrailingIconColor = Color.White,
        unfocusedTrailingIconColor = Color.White.copy(alpha = 0.6f)
    )
}

/** Standart Portio Metin Giriş Alanı */
@Composable
fun PortioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it, color = Color.White.copy(alpha = 0.5f)) } },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = isError,
            singleLine = singleLine,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = PortioShape.Medium,
            colors = portioTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = PortioColors.Error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }
    }
}

/** Arama Çubuğu (PortioSearchBar) */
@Composable
fun PortioSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ara…",
    onSearch: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = isTvDevice(context)
    var isInputModalOpen by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val shape = PortioShape.SearchInput

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isTv && isFocused) PortioColors.SurfaceRaised else Color.Black.copy(alpha = 0.55f))
            .border(
                width = if (isTv && isFocused) 2.5.dp else 1.dp,
                color = if (isTv && isFocused) PortioColors.FocusBorder else PortioColors.Hairline,
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(isTv)
            .clickable(isTv) { isInputModalOpen = true }
            .onKeyEvent { ev ->
                if (isTv && isTvSelectKey(ev)) {
                    isInputModalOpen = true; true
                } else false
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (isTv && isFocused) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        if (isTv) {
            Text(
                text = if (query.isNotBlank()) query else "$placeholder (OK tuşuna basın)",
                style = MaterialTheme.typography.bodyMedium,
                color = if (query.isNotBlank()) Color.White else PortioColors.TextMuted,
                modifier = Modifier.weight(1f)
            )
        } else {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isBlank()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    inner()
                }
            )
        }
        if (query.isNotBlank()) {
            IconButton(
                onClick = {
                    onQueryChange("")
                    onClear?.invoke()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Temizle",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (isTv && isInputModalOpen) {
        AlertDialog(
            onDismissRequest = { isInputModalOpen = false },
            title = { Text("Arama", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                var tempText by remember { mutableStateOf(query) }
                val focusReq = remember { FocusRequester() }
                Column {
                    OutlinedTextField(
                        value = tempText,
                        onValueChange = { tempText = it; onQueryChange(it) },
                        singleLine = true,
                        placeholder = { Text(placeholder) },
                        colors = portioTextFieldColors(),
                        shape = PortioShape.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusReq)
                    )
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(100)
                        runCatching { focusReq.requestFocus() }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    isInputModalOpen = false
                    onSearch?.invoke()
                }) {
                    Text("Tamam", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onQueryChange("")
                    onClear?.invoke()
                    isInputModalOpen = false
                }) {
                    Text("Temizle", color = Color.White.copy(0.6f))
                }
            },
            containerColor = PortioColors.SurfaceRaised,
            shape = PortioShape.Dialog
        )
    }
}

/** Portio Anahtar (Switch) */
@Composable
fun PortioSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.Black,
            checkedTrackColor = Color.White,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
            uncheckedBorderColor = PortioColors.Hairline
        ),
        modifier = modifier
    )
}

/** Portio Onay Kutusu (Checkbox) */
@Composable
fun PortioCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = Color.White,
            checkmarkColor = Color.Black,
            uncheckedColor = PortioColors.HairlineStrong
        ),
        modifier = modifier
    )
}

/** Portio Radyo Butonu */
@Composable
fun PortioRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        colors = RadioButtonDefaults.colors(
            selectedColor = Color.White,
            unselectedColor = PortioColors.HairlineStrong
        ),
        modifier = modifier
    )
}

/** Portio Kaydırıcı (Slider) */
@Composable
fun PortioSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = PortioColors.Hairline
        ),
        modifier = modifier
    )
}
