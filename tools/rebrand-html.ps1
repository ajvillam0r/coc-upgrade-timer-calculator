# rebrand-html.ps1
# One-off migration: replaces the shared favicon/theme block, the navbar logo and the
# page titles on every HTML page. Safe to run once.

$Root = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)

$newHead = @'
    <!-- Favicons -->
    <link rel="icon" type="image/svg+xml" href="assets/favicon/favicon.svg">
    <link rel="icon" type="image/png" sizes="32x32" href="assets/favicon/favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="assets/favicon/favicon-16x16.png">
    <link rel="apple-touch-icon" sizes="180x180" href="assets/favicon/apple-touch-icon.png">
    <link rel="icon" type="image/png" sizes="192x192" href="assets/favicon/android-chrome-192x192.png">

    <!-- Windows Tile -->
    <meta name="msapplication-TileColor" content="#0b1220">
    <meta name="msapplication-TileImage" content="assets/favicon/mstile-144x144.png">

    <!-- Theme Color (matches the app palette) -->
    <meta name="theme-color" content="#f5f7fa" media="(prefers-color-scheme: light)">
    <meta name="theme-color" content="#0b1220" media="(prefers-color-scheme: dark)">
'@

$titles = @{
    'index.html'           = 'Upgrade Timer Calc'
    'simple-calc.html'     = 'Simple Calc | Upgrade Timer Calc'
    'builder-potion.html'  = 'Builder Potion | Upgrade Timer Calc'
    'research-potion.html' = 'Research Potion | Upgrade Timer Calc'
    'pet-potion.html'      = 'Pet Potion | Upgrade Timer Calc'
    'about.html'           = 'About | Upgrade Timer Calc'
}

$pattern = '(?s)    <!-- Apple Touch Icon -->.*?<meta name="theme-color" content="#181818">'

Get-ChildItem (Join-Path $Root 'www') -Filter *.html | ForEach-Object {
    $path = $_.FullName
    $text = [System.IO.File]::ReadAllText($path)

    $text = [regex]::Replace($text, $pattern, $newHead.Replace('$', '$$'))

    $text = $text.Replace(
        '<div class="navbar__logo">Clash Potion Calculator</div>',
        '<div class="navbar__logo">Upgrade Timer Calc</div>')

    if ($titles.ContainsKey($_.Name)) {
        $text = [regex]::Replace($text, '<title>.*?</title>', ('<title>' + $titles[$_.Name] + '</title>'))
    }

    [System.IO.File]::WriteAllText($path, $text, $utf8)
}

Write-Output 'HTML head blocks, logos and titles updated.'
