package org.prauga.equalizer

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.prauga.pvot.designsystem.platform.PvotBaseActivity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.prauga.equalizer.ui.AppShell

class MainActivity : PvotBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPvotContent {
            PvotAppTheme {
                AppShell()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppShellPreview() {
    PvotAppTheme {
        AppShell()
    }
}
