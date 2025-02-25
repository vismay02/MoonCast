package com.venture.mooncast.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import com.venture.mooncast.R

@Composable
fun CalendarScreen() {
    Column {
        CalendarHeader()
    }
}

@Composable
internal fun CalendarHeader(currentMonth: String = "February 2025") {
    Row {
        Icon(
            ImageVector.vectorResource(R.drawable.ic_left_arrow),
            contentDescription = stringResource(R.string.left_arrow_desc)
        )
        BasicTextField(value = currentMonth, onValueChange = {})
        Icon(
            ImageVector.vectorResource(R.drawable.ic_right_arrow),
            contentDescription = stringResource(R.string.right_arrow_desc)
        )
    }
}

@Composable
@Preview(backgroundColor = 0XFFFFFF, showBackground = true)
private fun CalendarScreenPreview() {
    CalendarScreen()
}