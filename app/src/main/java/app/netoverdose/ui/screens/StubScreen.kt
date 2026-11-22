package app.netoverdose.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.netoverdose.R
import app.netoverdose.design.LocalDesign
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.theme.MonoFamily
import java.util.Locale

@Composable
fun StubScreen(@StringRes titleRes: Int) {
    val palette = LocalDesign.current
    AppBackground {
        Box(Modifier.fillMaxSize().padding(palette.pad), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(titleRes).uppercase(Locale.forLanguageTag("ru")),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp, letterSpacing = 1.04.sp),
                    color = palette.muted
                )
                RainbowHairline(Modifier.fillMaxWidth(0.3f), alpha = palette.rainAlpha)
                Text(
                    stringResource(R.string.stub_hint),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp),
                    color = palette.muted
                )
            }
        }
    }
}
