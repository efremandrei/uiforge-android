# UI Forge 0.1.48 (build 48)

Android UI/accessibility review, preserving the existing editor layout.

- Readable light/dark controls, dropdowns, hints, action icons, and OFF switches.
- Larger circular help controls, meaningful accessibility labels, and updated Help.
- Keyboard-aware Inspector with a fixed close control, reliable color swatches, and actual dp dimensions.
- Faster access to the canvas, tap-to-add objects, and edge scrolling during palette drags.
- Non-overlapping template placement and size-preserving dragging.
- Confirmation before discarding unsaved work or deleting an object.
- Flexible dialogs/fields, enlarged-text support, and a compact landscape banner.

## Verification

Debug assembly and lint passed (0 errors, 74 warnings). All 15 scripted UI assertions and 26 theme contrast checks passed. Reviewed compact portrait, 200% text size with keyboard, and landscape on an Android 15/API 35 emulator. No UI Forge exception appeared in the reviewed crash buffer.

Package `com.uiforge.app`, versionCode 48. This APK retains the build 47 signing certificate for in-place updates. Samsung/Android 16 hardware and a complete TalkBack walkthrough have not been verified.

APK: `UIForge-v0.1.48-build-48-debug.apk`

SHA256: `2264e18d4e636c74a041611f47f6ec3c36694618392f783a15a35f08c38f9d29`
