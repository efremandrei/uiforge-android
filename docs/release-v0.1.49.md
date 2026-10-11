# UI Forge 0.1.49 (build 49)

Includes the Android UI/accessibility improvements from build 48, plus the final Save dialog label-spacing correction.

- Readable light/dark controls, dropdowns, hints, icons, and OFF switches.
- 48dp circular help controls, descriptive accessibility labels, and updated Help.
- Keyboard-aware side Inspector with a fixed close button, color swatches, and actual dp dimensions.
- Go to canvas shortcut, tap-to-add objects, palette drag edge scrolling, and size-preserving widget movement.
- Non-overlapping template placement and confirmation before replacing unsaved work or deleting objects.
- Flexible dialogs and enlarged-text support; compact landscape banner.
- Save dialog now separates its floating label from the project name.
- Supplemental CI uses supported Android SDK packages.

## Verification

Build 48 passed 15 UI smoke assertions and 26 editor contrast checks, with compact portrait, 200% text/keyboard, and landscape review. Build 49 passes debug assembly, lint (0 errors, 74 warnings), and focused Save dialog checks in light/dark skins and at 200% text size. Package, signature, and upgrade installation were verified on an Android 15/API 35 emulator.

Package `com.uiforge.app`, versionCode 49. The APK retains the existing signing certificate for in-place updates. Samsung/Android 16 hardware and a complete TalkBack walkthrough have not been verified.

APK: `UIForge-v0.1.49-build-49-debug.apk`

SHA256: `6da8b859fbb5a6bb2ffe5d8164b24fe95478245a42b08d67fd8e25b1b68ca358`
