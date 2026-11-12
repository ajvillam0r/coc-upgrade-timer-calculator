# Upgrade Timer Calc

**Turn potion boosts into exact upgrade completion times.**

A free, ad-free, offline-first upgrade timer for Android, iPhone and the desktop.
Enter the time an upgrade has left, add the potions you are going to use, and the
app tells you the exact moment it finishes — then optionally rings an alarm.

<p>
  <a href="https://github.com/ajvillam0r/coc-upgrade-timer-calculator/releases/latest"><img alt="Download the latest APK" src="https://img.shields.io/badge/download-APK-10b981?style=flat-square"></a>
  <a href="https://ajvillam0r.github.io/coc-upgrade-timer-calculator/"><img alt="Open the web app" src="https://img.shields.io/badge/open-web%20app-0b1220?style=flat-square"></a>
  <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-d97706?style=flat-square"></a>
</p>

---

## Features

| | |
|---|---|
| **Builder Potion timer** | Calculate the finish time from the number of potions used, or from the boost time you have left. |
| **Research Potion timer** | Same maths for the Laboratory, including the "boost time left" mode. |
| **Pet Potion timer** | Pet House upgrades, with the same two input modes. |
| **General upgrade timer** | A plain days / hours / minutes calculator for anything that is not a potion. |
| **Built-in keypad** | A large on-screen numeric keypad on every calculator — no fiddly soft keyboard, no zooming. |
| **Android floating overlay** | Float a calculator bubble on top of your game and keep playing while you do the maths. |
| **Silent alarm** | On Android, schedule a system alarm for the exact completion moment — no notification spam. |
| **100% offline** | Every page, script and icon is cached by a service worker. After the first visit it works with no connection at all. |
| **Installable PWA** | Add it to your iPhone, iPad, Android or desktop home screen and it opens full-screen like a native app. |

---

## Install on Android (APK)

1. Open the [latest release](https://github.com/ajvillam0r/coc-upgrade-timer-calculator/releases/latest).
2. Download `app-debug.apk` (or the release APK attached to that release).
3. On your phone, tap the downloaded file.
4. Android will ask you to allow installs from this source — allow it for your
   browser or file manager, then tap **Install**.
5. Open **Upgrade Timer Calc**.

> The app needs **Android 7.0 (API 24)** or newer.
>
> **Float Mode** needs the "display over other apps" permission the first time you
> use it. **Set Alarm** hands the time to your phone's clock app.

Because the application id is `com.ajvillam0r.upgradetimercalc`, installing this
build will not overwrite any other calculator app. If you installed an older
development build with a different id, uninstall it first.

---

## Install on iPhone / iPad / Desktop (offline web app)

The web version is hosted on GitHub Pages and works exactly like the Android app,
minus the two Android-only extras (floating overlay and native alarm).

### iPhone / iPad (Safari)

1. Open **https://ajvillam0r.github.io/coc-upgrade-timer-calculator/** in **Safari**.
2. Tap the **Share** button.
3. Scroll down and tap **Add to Home Screen**.
4. Name it **Upgrade Timer** and tap **Add**.

It now launches full-screen from your home screen and works with no connection.

> iOS only allows "Add to Home Screen" from Safari. Chrome and Firefox on iOS
> cannot install it.

### Android (Chrome)

1. Open **https://ajvillam0r.github.io/coc-upgrade-timer-calculator/** in Chrome.
2. Tap the **⋮** menu and choose **Install app** / **Add to Home screen**.

### Desktop (Chrome, Edge, Brave)

1. Open **https://ajvillam0r.github.io/coc-upgrade-timer-calculator/**.
2. Click the **install icon** in the address bar, or **⋮ → Cast, save, and share →
   Install page as app**.

---

## Project layout

```
www/                        The web app (this is what GitHub Pages serves)
  index.html                Home screen
  simple-calc.html          General days / hours / minutes timer
  builder-potion.html       Builder Potion calculator
  research-potion.html      Research Potion calculator
  pet-potion.html           Pet Potion calculator
  about.html                About, licence and credits
  manifest.json             PWA manifest (name, icons, standalone display)
  sw.js                     Offline-first service worker
  robots.txt / sitemap.xml  Search engine files
  css/style.css             Theme, layout and keypad styles
  js/platform.js            Platform detection + service worker registration
  js/calculator.js          Potion and timer maths
  js/keypad.js              On-screen numeric keypad
  js/alarm.js               Android alarm bridge (hidden on web/iOS)
  js/floating.js            Android floating overlay (hidden on web/iOS)
  js/utils.js               Formatting helpers and navbar behaviour
  assets/                   Original SVG/PNG icons and favicons

android/                    Capacitor Android project
tools/generate-icons.ps1    Regenerates every launcher icon, splash and PWA icon
```

## Build from source

Requirements: **Node.js 20+**, **JDK 21**, **Android SDK 36**.

```powershell
npm install
npx cap sync android
cd android
.\gradlew.bat assembleDebug "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

For a release build, create a keystore and configure a `signingConfig` in
`android/app/build.gradle`, then run `assembleRelease` instead.

## Publishing the web app to GitHub Pages

The coc-upgrade-timer-calculatorsitory ships a ready-to-use workflow
(`.github/workflows/deploy-pages.yml`) that publishes the contents of `www/` as
the site root, so the app is served at `https://ajvillam0r.github.io/coc-upgrade-timer-calculator/`.

1. Push this coc-upgrade-timer-calculatorsitory to GitHub.
2. **Settings → Pages → Build and deployment → Source: GitHub Actions**.
3. Push to `main` once more (or run the workflow manually from the **Actions**
   tab). The site is live in about a minute.
4. Replace the `ajvillam0r` / `coc-upgrade-timer-calculator` placeholders (see below) and push again so the
   canonical URLs, sitemap and social cards point at the real address.

### Replacing the URL placeholders

Every canonical URL, Open Graph tag, the JSON-LD block, `robots.txt` and
`sitemap.xml` use the placeholder base:

```
https://ajvillam0r.github.io/coc-upgrade-timer-calculator/
```

Find and replace `ajvillam0r` with your GitHub ajvillam0r and `coc-upgrade-timer-calculator` with the
coc-upgrade-timer-calculatorsitory name, then commit:

```powershell
Get-ChildItem www -Recurse -File -Include *.html,*.txt,*.xml |
  ForEach-Object {
    $p = $_.FullName
    (Get-Content $p -Raw).
      Replace('ajvillam0r', 'your-ajvillam0r').
      Replace('coc-upgrade-timer-calculator', 'your-coc-upgrade-timer-calculator') |
      Set-Content $p -NoNewline -Encoding utf8
  }
```

The same `ajvillam0r` / `coc-upgrade-timer-calculator` tokens appear in this README's badge links and in
`www/index.html`'s `downloadUrl`.

## Regenerating the brand assets

All launcher icons, splash screens, favicons and PWA icons come from one original
vector design and are generated with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\generate-icons.ps1
```

---

## Legal

### Supercell Fan Content Policy

This material is unofficial and is not endorsed by Supercell. For more
information see [Supercell's Fan Content Policy](https://supercell.com/fan-content-policy).

Upgrade Timer Calc is not affiliated with, endorsed, sponsored, or specifically
approved by Supercell. All game names and trademarks belong to their respective
owners. No game artwork, sprites, logos or proprietary fonts are included in this
coc-upgrade-timer-calculatorsitory — every icon is an original SVG or a generated PNG.

### Licence

Released under the [MIT License](LICENSE).

The calculation logic and the original page structure are derived from
**[Clash Potion Calculator](https://github.com/LukasMFR/clash-potion-calculator)**
by **Lukas Mauffré**, used under the MIT License. His copyright notice is
preserved in [`LICENSE`](LICENSE) alongside the copyright for the modifications
in this coc-upgrade-timer-calculatorsitory.

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the full list of
components and their licences.
