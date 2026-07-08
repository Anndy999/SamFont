package com.samfont.core.samsung

object SamsungFontXmlBuilder {
    fun build(spec: SamsungFontPackageSpec): String {
        return buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<font displayname="${escapeXml(spec.displayName)}">""")
            appendLine("    <sans>")
            appendLine("        <file>")
            appendLine("            <filename>${escapeXml(spec.fontFileName)}</filename>")
            appendLine("            <droidname>${escapeXml(spec.droidName)}</droidname>")
            appendLine("        </file>")
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
