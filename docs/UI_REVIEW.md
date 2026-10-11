# Android UI review: builds 48-49

The editor keeps the existing banner, grouped UI Objects, local project actions, and side Inspector. The review applies Android accessibility and layout guidance to those established interactions.

## Findings and repairs

| Finding | Repair |
| --- | --- |
| A light-only base theme allowed default controls and dialogs to remain unreadable in dark skin. | Use Material DayNight with explicit surface, text, hint, error, and on-accent roles. |
| White labels on bright cyan actions had poor contrast; OFF switches blended into dark surfaces. | Dark foregrounds on bright accents, darker teal in light skin, stronger outlines, and distinct OFF thumb/track colors. |
| Small help targets lacked meaningful accessibility labels; palette groups rebuilt themselves when toggled. | 48dp help/group controls, circular help outlines, descriptive actions, expanded/collapsed semantics, and stable group views. |
| Canvas work required scrolling through all setup controls. | A direct Go to canvas action, tap-to-add alternative, and automatic edge scrolling during palette drags. |
| Fixed 96dp template offsets made taller widgets overlap. | Calculate initial positions from each widget's actual height and a 12dp gap. |
| Canvas items were rendered before the available width was measured. | Refresh after a width change using a posted callback, outside Android's measurement pass. |
| Dragging a full-width object silently changed its size. | Preserve the resolved width when converting it to free placement. |
| Width values were stale after handle resizing; full-width mode could retain an explicit width. | Display and adjust actual dimensions in dp; clear explicit width when enabling full width. |
| The popup Inspector had unreliable keyboard behavior and its close control could scroll away. | A modal right-side Dialog with a fixed header and independently scrolling form; account for keyboard, system bars, and cutouts. |
| Selected color swatches could be overwritten by TextInputLayout's internal compound drawables. | Use the field's dedicated start-icon slot with the actual selected color and no tint. |
| Fixed-height fields and dialogs could clip enlarged text or many saved projects. | Flexible field/menu/action heights, scrolling About and Load dialogs, equal-width alignment choices, and a smaller landscape banner. |
| Template, New, and Load actions could erase unsaved edits immediately. | Compare against the saved project state and ask before replacement; confirm object deletion. |
| Help described a removed tab/sheet UI. | Rewrite Help to match project actions, Templates, UI Objects, Live Preview, Layers, Inspector, JSON export, and About logs. |

## Guidance used

- [Android accessibility for Views](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views): at least 48dp touch targets and meaningful descriptions for controls.
- [Android accessibility and contrast](https://developer.android.com/guide/topics/ui/accessibility/apps): readable foreground/background combinations, including at least 4.5:1 for small text.
- [Android layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics): safe areas, keyboard-aware input, consistent spacing, and screen/orientation adaptation.
- [Edge-to-edge in Views](https://developer.android.com/develop/ui/views/layout/edge-to-edge): system bar and display cutout insets. The status bar stays black as requested.

## Repeating checks

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat :app:assembleDebug :app:lintDebug --no-daemon --no-problems-report
.\tools\check-contrast.ps1
.\tools\check-ui.ps1 -Serial emulator-5554
```

The UI smoke check exercises app screens and creates screenshots. It changes a sample project on the emulator; use a test emulator rather than a personal device. The contrast check covers 26 editor theme pairs across light and dark modes. Designer-chosen widget colors and opacity remain editable and are not forced to meet those thresholds.

The delivered debug APK preserves `com.uiforge.app` and the existing signing certificate and increments the version to 0.1.48/build 48. CI debug builds stay supplemental rather than replacing the update-compatible review release as Latest.

Device coverage is an Android 15/API 35 emulator. Android 16/Samsung hardware and a complete TalkBack walkthrough require separate device verification.

## Verified results

- Debug assembly and lint completed successfully: 0 errors, 74 warnings. Existing warnings are not a warning-free accessibility certification.
- All 15 scripted UI smoke assertions passed, including cursor editing, project persistence, deletion/replacement safety, dragging, and handle resizing.
- All 26 editor contrast checks passed.
- Compact portrait, 200% font scaling (including keyboard input), and a 2340x1080 landscape workspace were inspected. Landscape Inspector content and its fixed close button remained accessible.
- The final Save, Load, and Export labels fit at 200% font scaling.
- The crash buffer contained no UI Forge exception during the reviewed interactions.
- APK package/version and v2 signature verified. The signing certificate matches build 47; an emulator upgrade install succeeded.
- Final APK SHA256: `2264e18d4e636c74a041611f47f6ec3c36694618392f783a15a35f08c38f9d29`.

## Build 49 follow-up

The final dialog screenshot review exposed an overlapping floating hint in Save. Build 49 uses an outlined field with explicit padding, text/hint roles, and a flexible minimum height. The supplemental GitHub workflow also uses supported SDK packages instead of the retired `tools` package.

Build 49 debug assembly, lint, metadata, signature, and upgrade installation passed. The 15 integration assertions above passed on build 48; build 49 repeats focused Save dialog checks in both skins and at 200% font size. Signing continuity is unchanged.

Build 49 APK SHA256: `6da8b859fbb5a6bb2ffe5d8164b24fe95478245a42b08d67fd8e25b1b68ca358`.
