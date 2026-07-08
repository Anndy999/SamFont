package com.samfont.core.samsung

object SamsungFontXmlBuilder {
    fun build(spec: SamsungFontPackageSpec): String {
        return buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<font displayname="${escapeXml(spec.displayName)}">""")
            appendLine("    <sans>")
            appendLine("        <file>${escapeXml(spec.fontFileName)}</file>")
            appendLine("        <droidname>DroidSans</droidname>")
            appendLine("    </sans>")
            appendLine("</font>")
        }
    }

    fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
