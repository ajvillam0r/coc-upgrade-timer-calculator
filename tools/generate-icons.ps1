# generate-icons.ps1
# Regenerates every raster brand asset for Upgrade Timer Calc from one vector design:
#   - Android launcher icons (API 24-25 PNG fallbacks) and round variants
#   - Splash screens for all densities and orientations
#   - Web favicons
# The design is original: a deep-slate rounded tile with an emerald timer ring and
# amber hands. No third-party artwork is read or copied.

Add-Type -AssemblyName System.Drawing

$Root      = Split-Path -Parent $PSScriptRoot
$ResDir    = Join-Path $Root 'android\app\src\main\res'
$FavDir    = Join-Path $Root 'www\assets\favicon'
$IconDir   = Join-Path $Root 'www\assets\icons'
New-Item -ItemType Directory -Force -Path $IconDir | Out-Null

$SlateTop  = [System.Drawing.Color]::FromArgb(255, 22, 35, 58)
$SlateBase = [System.Drawing.Color]::FromArgb(255, 11, 18, 32)
$Emerald   = [System.Drawing.Color]::FromArgb(255, 16, 185, 129)
$Amber     = [System.Drawing.Color]::FromArgb(255, 245, 158, 11)

function New-RoundRectPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

# Draws the emblem in a normalised 108x108 design space scaled by $unit.
function Draw-Emblem([System.Drawing.Graphics]$g, [float]$cx, [float]$cy, [float]$unit) {
    $ringPen = New-Object System.Drawing.Pen($Emerald, [float](6 * $unit))
    $ringPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $ringPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

    $handPen = New-Object System.Drawing.Pen($Amber, [float](6 * $unit))
    $handPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $handPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

    $r = [float](24 * $unit)
    $g.DrawEllipse($ringPen, [float]($cx - $r), [float]($cy - $r), [float](2 * $r), [float](2 * $r))

    $g.DrawLine($handPen, $cx, $cy, $cx, [float]($cy - 13 * $unit))
    $g.DrawLine($handPen, $cx, $cy, [float]($cx + 11 * $unit), [float]($cy + 6 * $unit))

    $dotBrush = New-Object System.Drawing.SolidBrush($Emerald)
    $dot = [float](4 * $unit)
    $g.FillEllipse($dotBrush, [float]($cx - $dot), [float]($cy - $dot), [float](2 * $dot), [float](2 * $dot))

    $ringPen.Dispose()
    $handPen.Dispose()
    $dotBrush.Dispose()
}

function New-BrandTile([int]$size, [string]$path, [bool]$round) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    $rect = New-Object System.Drawing.RectangleF(0, 0, $size, $size)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $SlateTop, $SlateBase, 90)

    if ($round) {
        $g.FillEllipse($brush, 0, 0, $size, $size)
    } else {
        $shape = New-RoundRectPath 0 0 $size $size ([float]($size * 0.22))
        $g.FillPath($brush, $shape)
        $shape.Dispose()
    }

    Draw-Emblem $g ([float]($size / 2)) ([float]($size / 2)) ([float]($size / 108))

    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

function New-Splash([int]$w, [int]$h, [string]$path) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear($SlateBase)

    $min = [Math]::Min($w, $h)
    $unit = [float]($min * 0.22 / 48)
    Draw-Emblem $g ([float]($w / 2)) ([float]($h / 2)) $unit

    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# --- Android launcher icons (API 24-25 fallbacks) ---
$densities = @{ 'mdpi' = 48; 'hdpi' = 72; 'xhdpi' = 96; 'xxhdpi' = 144; 'xxxhdpi' = 192 }
foreach ($key in $densities.Keys) {
    $size = $densities[$key]
    $dir = Join-Path $ResDir ("mipmap-" + $key)
    New-BrandTile $size (Join-Path $dir 'ic_launcher.png') $false
    New-BrandTile $size (Join-Path $dir 'ic_launcher_round.png') $true
    $stale = Join-Path $dir 'ic_launcher_foreground.png'
    if (Test-Path $stale) { Remove-Item -Force $stale }
}

# --- Splash screens ---
$splashes = @(
    @{ p = 'drawable\splash.png';              w = 480;  h = 320  },
    @{ p = 'drawable-land-mdpi\splash.png';    w = 480;  h = 320  },
    @{ p = 'drawable-land-hdpi\splash.png';    w = 800;  h = 480  },
    @{ p = 'drawable-land-xhdpi\splash.png';   w = 1280; h = 720  },
    @{ p = 'drawable-land-xxhdpi\splash.png';  w = 1600; h = 960  },
    @{ p = 'drawable-land-xxxhdpi\splash.png'; w = 1920; h = 1280 },
    @{ p = 'drawable-port-mdpi\splash.png';    w = 320;  h = 480  },
    @{ p = 'drawable-port-hdpi\splash.png';    w = 480;  h = 800  },
    @{ p = 'drawable-port-xhdpi\splash.png';   w = 720;  h = 1280 },
    @{ p = 'drawable-port-xxhdpi\splash.png';  w = 960;  h = 1600 },
    @{ p = 'drawable-port-xxxhdpi\splash.png'; w = 1280; h = 1920 }
)
foreach ($s in $splashes) {
    New-Splash $s.w $s.h (Join-Path $ResDir $s.p)
}

# --- Web favicons ---
New-BrandTile 16  (Join-Path $FavDir 'favicon-16x16.png') $false
New-BrandTile 32  (Join-Path $FavDir 'favicon-32x32.png') $false
New-BrandTile 144 (Join-Path $FavDir 'mstile-144x144.png') $false
New-BrandTile 180 (Join-Path $FavDir 'apple-touch-icon.png') $false
New-BrandTile 192 (Join-Path $FavDir 'android-chrome-192x192.png') $false

# --- PWA icons (manifest.json) ---
# "any" icons keep the rounded tile; the maskable icon fills the whole square and
# draws the emblem inside the 80% safe zone so Android can crop it to any shape.
function New-MaskableTile([int]$size, [string]$path) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    $rect = New-Object System.Drawing.RectangleF(0, 0, $size, $size)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $SlateTop, $SlateBase, 90)
    $g.FillRectangle($brush, 0, 0, $size, $size)

    Draw-Emblem $g ([float]($size / 2)) ([float]($size / 2)) ([float]($size * 0.72 / 108))

    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

New-BrandTile 192 (Join-Path $IconDir 'icon-192.png') $false
New-BrandTile 512 (Join-Path $IconDir 'icon-512.png') $false
New-MaskableTile 512 (Join-Path $IconDir 'icon-maskable-512.png')

Write-Output 'Brand icons, splash screens, favicons and PWA icons regenerated.'
