package com.example.gymtime.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.PrimaryAccent
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextTertiary

@Composable
fun HomeHeader(userName: String) {
    Column {
        // "WELCOME BACK" in muted gray
        Text(
            text = "WELCOME BACK",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // User name in large bold text
        Text(
            text = userName,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Split-color app name
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = TextPrimary)) {
                    append("Iron")
                }
                withStyle(style = SpanStyle(color = PrimaryAccent)) {
                    append("Log")
                }
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
