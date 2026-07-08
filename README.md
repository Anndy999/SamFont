# SamFont

SamFont is an Android/Kotlin/Jetpack Compose tool for importing, previewing, and packaging fonts for Samsung devices.

## Samsung font package install flow

The current MVP implements **font package installation**, not forced system font switching.

Flow:

1. User imports a `.ttf`, `.otf`, or `.ttc` file.
2. SamFont previews the font locally.
3. SamFont generates a clean-room Samsung/FlipFont-style APK from a local template.
4. The generated APK uses a fixed package name:
   `com.monotype.android.font.samfont.generated`
5. SamFont signs the APK with an app-private AndroidKeyStore key.
6. With authorized Shizuku, SamFont copies the APK to `/data/local/tmp`.
7. SamFont installs it through Package Installer session commands:
   `pm install-create`, `pm install-write`, `pm install-commit`.
8. SamFont verifies installation using `pm list packages`, `pm path`, and `dumpsys package`.
9. If package verification succeeds, the state becomes `SystemInstalled`.

`Applied` is shown only when SamFont can verify the current system font is actually the target font. The MVP does not mark a font as applied after package installation alone.

If Samsung Settings does not show the generated font package, provide the backend log. ROM-specific metadata or XML layout may need adaptation.

## Safety boundaries

- SamFont does not write to `/system/fonts`.
- SamFont does not modify system partitions.
- SamFont does not run arbitrary user-provided shell commands.
- Shizuku is used only for bounded package installation and package verification commands.
