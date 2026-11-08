package app.n4c.ark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Root() }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    var dynamic by remember { mutableStateOf(ArkPrefs.dynamic(ctx)) }
    var onboarded by remember { mutableStateOf(ArkPrefs.onboarded(ctx)) }

    ArkTheme(dynamic) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Crossfade(targetState = onboarded, label = "root") { done ->
                if (done) {
                    HomeScreen(dynamicColor = dynamic, onDynamicChange = {
                        dynamic = it
                        ArkPrefs.setDynamic(ctx, it)
                    })
                } else {
                    Onboarding(onFinish = {
                        ArkPrefs.setOnboarded(ctx)
                        onboarded = true
                    })
                }
            }
        }
    }
}
