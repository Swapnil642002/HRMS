package com.daksha.hrms.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daksha.hrms.ui.theme.*

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val cleanStatus = status.uppercase().replace('_', ' ')
    val (bgColor, textColor) = when (cleanStatus) {
        "APPROVED", "ACTIVE", "PAID", "COMPLETED", "FINALIZED" -> Pair(Emerald100, Emerald600)
        "REJECTED", "REVOKED", "INACTIVE" -> Pair(Rose100, Rose600)
        "PENDING", "DRAFT", "OPEN" -> Pair(Amber100, Amber600)
        else -> Pair(Slate100, Slate700)
    }

    Text(
        text = cleanStatus,
        color = textColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
