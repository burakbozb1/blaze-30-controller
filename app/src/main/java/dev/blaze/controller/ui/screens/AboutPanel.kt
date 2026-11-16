package dev.blaze.controller.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.R
import dev.blaze.controller.ui.components.PrimaryButton
import dev.blaze.controller.ui.theme.Pb

const val LINKEDIN_URL = "https://www.linkedin.com/in/burakbozb1"
const val LINKEDIN_BUTTON = "LINKEDIN'DE BURAK BOZ"

/** Content of the side panel that slides in from the left edge: what the app is, who made it, and a link. */
@Composable
fun AboutPanel(onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.logo), contentDescription = null, Modifier.size(96.dp))
        Text("BLAZE 30 CONTROLLER", Modifier.padding(top = 12.dp), color = Pb.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text("Yalnızca Kozmos Blaze 30 amfisiyle çalışır", Modifier.padding(top = 2.dp), color = Pb.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        Text(
            "Amfinin tonunu, gain ve ses ayarlarını, EQ'sunu, delay ve reverb'ünü Bluetooth ile telefondan " +
                "yönetirsin; akort için telefonun mikrofonunu kullanır. Yalnızca Bluetooth ve mikrofon izni ister, " +
                "internet erişimi yoktur; ses ve verilerin telefondan dışarı çıkmaz.",
            Modifier.padding(top = 16.dp), color = Pb.Text, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
        )
        Text("Geliştirici: Burak Boz", Modifier.padding(top = 16.dp, bottom = 16.dp), color = Pb.TextDim, fontSize = 12.sp)
        PrimaryButton(LINKEDIN_BUTTON, { onOpenUrl(LINKEDIN_URL) })
    }
}
