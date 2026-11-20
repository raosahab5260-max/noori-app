package com.noori.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RowScope.NavPill(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
            .background(if (active) Color(0xFF581C87) else Color.Transparent)
            .clickable { onClick() }.padding(vertical = 7.dp, horizontal = 3.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = if (active) Color.White else Color(0xFFC084FC), modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, color = Color.White, fontSize = 9.sp, maxLines = 1)
    }
}
