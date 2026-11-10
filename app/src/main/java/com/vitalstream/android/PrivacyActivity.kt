package com.vitalstream.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitalstream.android.ui.theme.VitalStreamAndroidTheme

class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VitalStreamAndroidTheme {
            Text("VitalStream imports resting heart rate only when you request it. Imported readings are stored in this app and uploaded to your configured VitalStream server for personal trends. Revoke access in Health Connect at any time. Revocation stops new imports; it does not delete previously imported records. This development build is not a diagnostic tool. Use synthetic data until you have reviewed the server and privacy setup.", Modifier.padding(24.dp))
        } }
    }
}
