package com.vknxworks.printctrl.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Widest a list or page gets before it stops growing and is centred instead. */
val ReadableWidth: Dp = 760.dp

/**
 * Keeps a list or page at a readable width on wide windows (landscape, tablets, unfolded foldables),
 * centred in the space. On a phone in portrait it's the full width, as before.
 */
fun Modifier.readableWidth(max: Dp = ReadableWidth): Modifier =
    this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max)
