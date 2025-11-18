package com.example.gymtime.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.example.gymtime.ui.theme.PrimaryAccent
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.GymTimeTheme

import com.example.gymtime.R
import com.example.gymtime.ui.theme.PrimaryAccentDark
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun StartWorkout(hasActiveRoutine: Boolean, nextWorkoutName: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { /* TODO: Handle Quick Start */ },
            modifier = Modifier
                .weight(1f)
                .height(150.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_play_arrow),
                    contentDescription = "Quick Start",
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Quick Start", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }

        if (hasActiveRoutine) {
            Button(
                onClick = { /* TODO: Handle Continue Routine */ },
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_forward),
                        contentDescription = "Continue Routine",
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Continue Routine", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (nextWorkoutName != null) {
                        Text(text = nextWorkoutName, fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f).height(150.dp), contentAlignment = Alignment.Center) {
                Text(text = "No Active Routine", textAlign = TextAlign.Center)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StartWorkoutPreviewWithActiveRoutine() {
    GymTimeTheme {
        StartWorkout(hasActiveRoutine = true, nextWorkoutName = "Legs")
    }
}

@Preview(showBackground = true)
@Composable
fun StartWorkoutPreviewWithoutActiveRoutine() {
    GymTimeTheme {
        StartWorkout(hasActiveRoutine = false, nextWorkoutName = null)
    }
}
