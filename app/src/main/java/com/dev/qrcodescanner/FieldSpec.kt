package com.dev.qrcodescanner

import android.net.Uri
import androidx.compose.ui.text.input.KeyboardType

const val WifiSecurityKey = "wifi_security"

val WifiSecurityOptions = listOf("WPA", "WEP", "None")

data class FieldSpec(
    val key: String,
    val label: String,
    val placeholder: String = "",
    val keyboardType: KeyboardType = KeyboardType.Text,
    val multiline: Boolean = false,
    val required: Boolean = false
)

enum class ContentType(val label: String, val fields: List<FieldSpec>) {
    Text(
        "Text",
        listOf(
            FieldSpec(
                key = "text",
                label = "Message",
                placeholder = "Enter any text",
                multiline = true,
                required = true
            )
        )
    ),
    Link(
        "Link",
        listOf(
            FieldSpec(
                key = "url",
                label = "Website address",
                placeholder = "example.com",
                keyboardType = KeyboardType.Uri,
                required = true
            )
        )
    ),
    WiFi(
        "Wi-Fi",
        listOf(
            FieldSpec(key = "ssid", label = "Network name", required = true),
            FieldSpec(key = "wifi_password", label = "Password")
        )
    ),
    Contact(
        "Contact",
        listOf(
            FieldSpec(key = "contact_name", label = "Full name", required = true),
            FieldSpec(
                key = "contact_phone",
                label = "Phone number",
                keyboardType = KeyboardType.Phone
            ),
            FieldSpec(
                key = "contact_email",
                label = "Email address",
                keyboardType = KeyboardType.Email
            ),
            FieldSpec(key = "contact_org", label = "Organization")
        )
    ),
    Email(
        "Email",
        listOf(
            FieldSpec(
                key = "email_to",
                label = "Recipient",
                keyboardType = KeyboardType.Email,
                required = true
            ),
            FieldSpec(key = "email_subject", label = "Subject"),
            FieldSpec(key = "email_body", label = "Message", multiline = true)
        )
    ),
    Phone(
        "Phone",
        listOf(
            FieldSpec(
                key = "phone_number",
                label = "Phone number",
                keyboardType = KeyboardType.Phone,
                required = true
            )
        )
    ),
    Sms(
        "SMS",
        listOf(
            FieldSpec(
                key = "sms_number",
                label = "Phone number",
                keyboardType = KeyboardType.Phone,
                required = true
            ),
            FieldSpec(key = "sms_message", label = "Message", multiline = true)
        )
    ),
    Barcode(
        "Barcode",
        listOf(
            FieldSpec(
                key = "barcode",
                label = "Barcode value",
                placeholder = "Letters or numbers",
                required = true
            )
        )
    );

    fun buildPayload(values: Map<String, String>): String? {
        fun value(key: String) = values[key].orEmpty().trim()

        if (fields.any { it.required && value(it.key).isEmpty() }) return null

        return when (this) {
            Text -> values["text"].orEmpty()
            Link -> normalizeUrl(value("url"))
            WiFi -> {
                val security = values[WifiSecurityKey] ?: "WPA"
                val password = value("wifi_password")
                val ssid = escapeWifi(value("ssid"))
                if (security == "None" || password.isEmpty()) {
                    "WIFI:T:nopass;S:$ssid;;"
                } else {
                    "WIFI:T:$security;S:$ssid;P:${escapeWifi(password)};;"
                }
            }

            Contact -> listOfNotNull(
                "BEGIN:VCARD",
                "VERSION:3.0",
                "N:;${value("contact_name")};;;",
                "FN:${value("contact_name")}",
                value("contact_org").takeIf { it.isNotEmpty() }?.let { "ORG:$it" },
                value("contact_phone").takeIf { it.isNotEmpty() }?.let { "TEL:$it" },
                value("contact_email").takeIf { it.isNotEmpty() }?.let { "EMAIL:$it" },
                "END:VCARD"
            ).joinToString("\n")

            Email -> {
                val params = listOfNotNull(
                    value("email_subject").takeIf { it.isNotEmpty() }
                        ?.let { "subject=${Uri.encode(it)}" },
                    value("email_body").takeIf { it.isNotEmpty() }
                        ?.let { "body=${Uri.encode(it)}" }
                )
                val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
                "mailto:${value("email_to")}$query"
            }

            Phone -> "tel:${value("phone_number")}"
            Sms -> "SMSTO:${value("sms_number")}:${value("sms_message")}"
            Barcode -> value("barcode")
        }
    }
}

fun normalizeUrl(url: String): String {
    val trimmed = url.trim()
    return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

private fun escapeWifi(value: String): String = buildString {
    value.forEach { char ->
        if (char in "\\;,:\"") append('\\')
        append(char)
    }
}