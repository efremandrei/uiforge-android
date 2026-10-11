param(
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Serial = 'emulator-5554',
    [string]$OutputDirectory = "$env:TEMP\uiforge-ui-review-build48",
    [switch]$HelpersOnly
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Invoke-Adb([string[]]$Arguments) {
    $result = & $Adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $result" }
    return $result
}
function Read-Ui {
    for ($attempt = 0; $attempt -lt 4; $attempt++) {
        $dump = Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/uiforge-review.xml')
        if (($dump -join ' ').Contains('dumped to:')) {
            Invoke-Adb @('pull', '/sdcard/uiforge-review.xml', "$OutputDirectory\current.xml") | Out-Null
            return [xml](Get-Content -LiteralPath "$OutputDirectory\current.xml" -Raw)
        }
        Start-Sleep -Milliseconds 700
    }
    throw 'Could not obtain an idle UI hierarchy'
}
function Find-Node([string]$Selector) {
    for ($attempt = 0; $attempt -lt 4; $attempt++) {
        $ui = Read-Ui
        $node = $ui.SelectSingleNode($Selector)
        if ($node) { return $node }
        Start-Sleep -Milliseconds 700
    }
    throw "UI element missing: $Selector"
}
function Bounds($Node) {
    $numbers = [regex]::Matches($Node.GetAttribute('bounds'), '\d+')
    return @($numbers | ForEach-Object { [int]$_.Value })
}
function Tap([string]$Selector) {
    $box = Bounds (Find-Node $Selector)
    Invoke-Adb @('shell', 'input', 'tap', [string][int](($box[0] + $box[2]) / 2),
        [string][int](($box[1] + $box[3]) / 2)) | Out-Null
}
function Id([string]$Name) { return "//node[@resource-id='com.uiforge.app:id/$Name']" }
function Back { Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK') | Out-Null }
function Screenshot([string]$Name) {
    Start-Sleep -Milliseconds 700
    Invoke-Adb @('shell', 'screencap', '-p', '/sdcard/uiforge-review.png') | Out-Null
    Invoke-Adb @('pull', '/sdcard/uiforge-review.png', "$OutputDirectory\$Name.png") | Out-Null
}
function Check([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
    Write-Output "PASS: $Message"
}
function Scroll-Top {
    for ($i = 0; $i -lt 4; $i++) {
        Invoke-Adb @('shell', 'input', 'swipe', '100', '600', '100', '1800', '180') | Out-Null
    }
}
if ($HelpersOnly) { return }

Invoke-Adb @('shell', 'am', 'force-stop', 'com.uiforge.app') | Out-Null
Invoke-Adb @('shell', 'am', 'start', '-n', 'com.uiforge.app/.MainActivity') | Out-Null
$skin = Find-Node (Id 'darkSkinSwitch')
if ($skin.GetAttribute('checked') -eq 'true') { Tap (Id 'darkSkinSwitch') }
Screenshot '01-light'
$helpBox = Bounds (Find-Node (Id 'heroHelpButton'))
$densityResult = Invoke-Adb @('shell', 'wm', 'density')
$density = [int]([regex]::Matches(($densityResult -join ' '), '\d+')[-1].Value)
Check (($helpBox[2] - $helpBox[0]) -ge [Math]::Floor(48 * $density / 160)) 'Help has a 48dp touch target'
Check ((Find-Node (Id 'heroHelpButton')).GetAttribute('content-desc') -eq 'Help with PROJECT NAME') 'Help has a descriptive accessibility label'
Tap (Id 'presetDropdownInput')
Start-Sleep -Milliseconds 700
Screenshot '02-templates-light'
Back
Tap (Id 'bottomHelpButton')
$helpUi = Read-Ui
Check ($helpUi.OuterXml.Contains('Go to canvas') -and !$helpUi.OuterXml.Contains('Build tab')) 'Help describes the current UI'
Screenshot '03-help'
Back
Tap (Id 'bottomAboutButton')
$versionText = Get-Content "$PSScriptRoot\..\app\build.gradle" -Raw
$versionName = [regex]::Match($versionText, 'versionName\s+"([^"\s]+)"').Groups[1].Value
$versionCode = [regex]::Match($versionText, 'versionCode\s+(\d+)').Groups[1].Value
Find-Node "//node[@text='Version: $versionName/$versionCode']" | Out-Null
Screenshot '04-about'
Tap "//node[@text='Read logs']"
Find-Node "//node[@text='App logs']" | Out-Null
Back
Back
Tap (Id 'darkSkinSwitch')
Check ((Find-Node (Id 'darkSkinSwitch')).GetAttribute('checked') -eq 'true') 'Dark skin survives Activity recreation'
Screenshot '05-dark'
Tap (Id 'goToCanvasButton')
Start-Sleep -Milliseconds 700
$previewHelp = Bounds (Find-Node (Id 'previewHelpButton'))
$banner = Bounds (Find-Node (Id 'appBanner'))
Check ($previewHelp[1] -ge $banner[3]) 'Canvas shortcut keeps the entire heading and help control visible'
Screenshot '06-canvas'
Tap "//node[@content-desc='Button: Shop the edit']"
Find-Node (Id 'inspectorScroll') | Out-Null
Screenshot '07-inspector-dark'
$original = (Find-Node (Id 'componentTitleInput')).GetAttribute('text')
Tap (Id 'componentTitleInput')
Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_MOVE_HOME', 'KEYCODE_DPAD_RIGHT',
    'KEYCODE_DPAD_RIGHT', 'KEYCODE_DPAD_RIGHT', 'KEYCODE_DEL', 'KEYCODE_DEL') | Out-Null
$edited = (Find-Node (Id 'componentTitleInput')).GetAttribute('text')
Check ($edited -eq ($original.Substring(0, 1) + $original.Substring(3))) 'Repeated deletion preserves the cursor position'
Screenshot '08-inspector-keyboard'
Back
Find-Node (Id 'closeInspectorDrawerButton') | Out-Null
Invoke-Adb @('shell', 'input', 'swipe', '850', '1750', '850', '700', '300') | Out-Null
Find-Node (Id 'closeInspectorDrawerButton') | Out-Null
Screenshot '09-inspector-scrolled'
Check ($null -ne (Find-Node (Id 'closeInspectorDrawerButton'))) 'Inspector close control stays visible when scrolling'
Tap (Id 'closeInspectorDrawerButton')
Scroll-Top
Tap (Id 'startNewDesignButton')
Find-Node "//node[@text='Discard unsaved changes?']" | Out-Null
Tap "//node[@resource-id='android:id/button2']"
Check ((Find-Node (Id 'projectNameInput')).GetAttribute('text') -eq 'Commerce Checkout') 'Canceling replacement preserves the edited project'
Tap (Id 'saveProjectButton')
Find-Node "//node[@text='Save project locally']" | Out-Null
Screenshot '10-save-dark'
Tap "//node[@resource-id='android:id/button1']"
$ui = Read-Ui
if ($ui.OuterXml.Contains('Replace saved project?')) { Tap "//node[@resource-id='android:id/button1']" }
Tap (Id 'exportButton')
Find-Node "//node[@text='Design JSON']" | Out-Null
Screenshot '11-export-dark'
Back
Tap (Id 'startNewDesignButton')
Check ((Find-Node (Id 'projectNameInput')).GetAttribute('text') -eq 'Untitled Flow') 'New design opens after saving without a discard prompt'
Tap (Id 'loadProjectButton')
Find-Node "//node[@text='Load saved project']" | Out-Null
Screenshot '12-load-dark'
Tap "//node[@text='COMMERCE CHECKOUT']"
Check ((Find-Node (Id 'projectNameInput')).GetAttribute('text') -eq 'Commerce Checkout') 'Saved project loads with its name'
Tap (Id 'goToCanvasButton')
Find-Node "//node[@content-desc='Button: $edited']" | Out-Null
Check ($true) 'Saved title edits survive save and load'
$objectSelector = "//node[@content-desc='Button: $edited']"
$beforeMove = Bounds (Find-Node $objectSelector)
$centerX = [int](($beforeMove[0] + $beforeMove[2]) / 2)
$centerY = [int](($beforeMove[1] + $beforeMove[3]) / 2)
Invoke-Adb @('shell', 'input', 'swipe', "$centerX", "$centerY", "$centerX", "$($centerY - 100)", '180') | Out-Null
$afterMove = Bounds (Find-Node $objectSelector)
Check ($afterMove[1] -lt $beforeMove[1]) 'Dragging moves an existing canvas object'
Check (($afterMove[2] - $afterMove[0]) -eq ($beforeMove[2] - $beforeMove[0])) 'Dragging a full-width object preserves its size'
$centerY = [int](($afterMove[1] + $afterMove[3]) / 2)
Invoke-Adb @('shell', 'input', 'swipe', "$centerX", "$centerY", "$centerX", "$centerY", '650') | Out-Null
Screenshot '13-resize-handles'
$bottomY = $afterMove[3] - [int](14 * $density / 160)
Invoke-Adb @('shell', 'input', 'swipe', "$centerX", "$bottomY", "$centerX", "$($bottomY + 65)", '220') | Out-Null
$afterResize = Bounds (Find-Node $objectSelector)
Check (($afterResize[3] - $afterResize[1]) -gt ($afterMove[3] - $afterMove[1])) 'Long press and bottom handle resize the canvas object'
Screenshot '14-resized'
$crashes = Invoke-Adb @('logcat', '-d', '-b', 'crash', '-t', '100')
Check (!(($crashes -join "`n") -match 'Process: com\.uiforge\.app')) 'No UI Forge crash in emulator crash buffer'
Write-Output "Screenshots and UI dump: $OutputDirectory"
