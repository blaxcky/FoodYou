package com.maksimowiczm.foodyou.app.infrastructure.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.maksimowiczm.foodyou.R

class HealthConnectRationaleActivity : FoodYouAbstractActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = resources.getDimensionPixelSize(R.dimen.health_connect_rationale_padding)
        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padding, padding, padding, padding)
            }

        val title =
            TextView(this).apply {
                text = "Health Connect steps"
                textSize = 24f
            }
        val message =
            TextView(this).apply {
                text =
                    "Food You reads your step count from Health Connect only to estimate activity calories."
                textSize = 16f
            }
        val privacy =
            Button(this).apply {
                text = "Privacy policy"
                setOnClickListener {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://foodyou.maksimowiczm.com/privacy-policy"),
                        )
                    )
                }
            }

        layout.addView(title)
        layout.addView(message)
        layout.addView(privacy)
        setContentView(layout)
    }
}
