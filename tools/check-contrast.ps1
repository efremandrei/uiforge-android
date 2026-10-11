$ErrorActionPreference = 'Stop'
$resourceRoot = Join-Path $PSScriptRoot '..\app\src\main\res'
function Luminance([string]$Hex) {
    $hexValue = $Hex.TrimStart('#')
    $linear = foreach ($offset in @(0, 2, 4)) {
        $channel = [Convert]::ToInt32($hexValue.Substring($offset, 2), 16) / 255.0
        if ($channel -le 0.04045) { $channel / 12.92 }
        else { [Math]::Pow(($channel + 0.055) / 1.055, 2.4) }
    }
    return 0.2126 * $linear[0] + 0.7152 * $linear[1] + 0.0722 * $linear[2]
}
$checks = @(
    @('text_strong', 'surface_primary', 4.5),
    @('text_soft', 'surface_primary', 4.5),
    @('text_soft', 'surface_base', 4.5),
    @('hero_text', 'hero_input', 4.5),
    @('hero_text_soft', 'hero_input', 4.5),
    @('on_mint', 'accent_mint', 4.5),
    @('on_accent', 'accent_cobalt', 4.5),
    @('on_secondary', 'accent_gold', 4.5),
    @('accent_cobalt', 'surface_selected', 4.5),
    @('accent_sunset', 'surface_primary', 4.5),
    @('stroke_soft', 'surface_primary', 3.0),
    @('switch_track_off', 'surface_primary', 3.0),
    @('switch_thumb_off', 'switch_track_off', 3.0)
)
foreach ($mode in @('values', 'values-night')) {
    $colors = @{}
    ([xml](Get-Content -LiteralPath "$resourceRoot\$mode\colors.xml" -Raw)).resources.color |
        ForEach-Object { $colors[$_.name] = $_.InnerText }
    foreach ($pair in $checks) {
        $a = Luminance $colors[$pair[0]]
        $b = Luminance $colors[$pair[1]]
        $ratio = ([Math]::Max($a, $b) + 0.05) / ([Math]::Min($a, $b) + 0.05)
        if ($ratio -lt $pair[2]) { throw "$mode $($pair[0]) / $($pair[1]) contrast is $ratio; requires $($pair[2])" }
        Write-Output ('PASS: {0} {1}/{2} = {3:N2}:1' -f $mode, $pair[0], $pair[1], $ratio)
    }
}
