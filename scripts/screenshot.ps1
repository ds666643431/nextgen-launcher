# Capture a device screenshot and downscale/compress to < ~190 KB JPEG.
# Usage: scripts\screenshot.ps1 -Device 39051FDJH00FEP -Out shot.jpg
param(
    [string]$Device = "39051FDJH00FEP",
    [string]$Out = "shot.jpg",
    [int]$MaxWidth = 1000,
    [int]$QualityStart = 80
)
$ErrorActionPreference = "Continue"
$remote = "/sdcard/nextgenlauncher_shot.png"
adb -s $Device shell screencap -p $remote | Out-Null
$rawPng = [System.IO.Path]::ChangeExtension($Out, ".raw.png")
adb -s $Device pull $remote $rawPng | Out-Null
adb -s $Device shell rm $remote | Out-Null

Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile((Resolve-Path $rawPng))
$scale = [Math]::Min(1.0, $MaxWidth / $img.Width)
$w = [int]($img.Width * $scale); $h = [int]($img.Height * $scale)
$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = 'HighQualityBicubic'
$g.DrawImage($img, 0, 0, $w, $h)
$img.Dispose()

$enc = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
for ($q = $QualityStart; $q -ge 30; $q -= 10) {
    $ep = New-Object System.Drawing.Imaging.EncoderParameters 1
    $ep.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, [long]$q)
    $bmp.Save($Out, $enc, $ep)
    if ((Get-Item $Out).Length -lt 190KB) { break }
}
$bmp.Dispose()
Remove-Item $rawPng -ErrorAction SilentlyContinue
Write-Output ("{0} : {1} KB ({2}x{3})" -f $Out, [int]((Get-Item $Out).Length / 1KB), $w, $h)
