package com.toolbill.android.core.design.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText

/**
 * The Toolbill text field.
 *
 * The label lives *inside* the border above the value, rather than floating on it as Material's
 * outlined field does. That is what lets a 104dp currency selector sit flush beside a flexible
 * amount field: both are the same box, so both are the same height by construction rather than
 * by coincidence.
 *
 * Geometry from the design: `border 1px, radius 8, padding 8px 14px`, label `500 11/1.2`,
 * value `400 16/24`. Focus thickens the border to 2dp and tints the label.
 */
@Composable
fun ToolbillTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    valueStyle: TextStyle = ToolbillText.fieldValue,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val accent = when {
        isError -> scheme.error
        focused -> scheme.primary
        else -> null
    }
    val borderWidth = if (accent != null) 2.dp else 1.dp
    val borderColor = accent ?: if (enabled) scheme.outline else scheme.outlineVariant
    // A 2dp border eats 1dp of the box, so the inner padding compensates and the height holds.
    val inset = if (accent != null) 13.dp else 14.dp
    val vertical = if (accent != null) 7.dp else 8.dp

    Column(
        modifier = modifier
            .border(borderWidth, borderColor, Radius.sm)
            .padding(horizontal = inset, vertical = vertical),
    ) {
        Text(
            text = label,
            style = ToolbillText.fieldLabel,
            color = accent ?: scheme.onSurfaceVariant,
        )
    BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = valueStyle.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = placeholder, style = valueStyle, color = scheme.outline)
                }
                inner()
            },
        )
    }
}

/**
 * A field-shaped selector — same box as [ToolbillTextField] so it lines up with one beside it.
 *
 * [width] is fixed at 104dp for the currency slot, which is what the design allots it.
 */
@Composable
fun ToolbillSelectField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    valueStyle: TextStyle = ToolbillText.fieldValue,
    onClick: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .border(1.dp, scheme.outline, Radius.sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = label, style = ToolbillText.fieldLabel, color = scheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = value, style = valueStyle, color = scheme.onSurface)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.height(18.dp),
            )
        }
    }
}

/** The error or hint line under a field. */
@Composable
fun FieldMessage(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = text,
        style = ToolbillText.rowSupport,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = modifier,
    )
}

/** Section label above a control group — `450 10.5px mono, .1em, uppercase`. */
@Composable
fun FieldSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = ToolbillText.microLabel.copy(fontSize = 10.5.sp),
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier,
    )
}
