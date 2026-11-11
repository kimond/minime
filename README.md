# minime

A calm, text-first Android launcher. No icon grid, no feed: a clock, the weather, today's date, a row of shortcuts and a short list of pinned apps. Everything else is one swipe away, and it's built to help you notice how you use your phone.

Kotlin + Jetpack Compose, no Google Play Services, no trackers, no network access except the weather.

## Features

**Home screen**
- Large thin clock with the weather beside it (temperature, conditions, city, high/low)
- One-line date: `SUNDAY · NOVEMBER 8 · 2026`
- Battery and Wi-Fi pills in place of the system status bar
- A row of round shortcut buttons (Notes, Math, Cal, …), each with a customizable label, icon and target app
- Up to 9 pinned apps, each showing today's screen time

**App list** (swipe up)
- Pill search field with accent-insensitive, ranked matching: prefix, word start, initials (`gm` → Google Maps), anywhere
- Opens automatically when only one app matches (optional)
- Today's screen time at the end of each row, plus a daily total
- Long-press to pin, rename, hide, view app info or uninstall
- Work-profile apps supported

**Behaviour insights** (swipe right, opt-in)
- Today against a *typical day at this hour*, unlocks, app opens, daily average
- 24-hour chart (today vs. typical), 7-day chart, most-used apps
- Plain-language patterns: checking habits, longest stretch, late-night use, the app your day starts with, busiest hour, weekly trend
- Computed on the phone from the last 7 days of usage events; nothing is stored or sent

**Notification shield**
- Pick apps to mute; their notifications are dismissed as they arrive
- Ongoing notifications (music, calls, navigation) are never touched
- A "held back" list so you can review what you missed

**Also:** portrait-locked, optional wallpaper, left/center/right alignment, °C/°F.

## Gestures

| Where | Gesture | Does |
|---|---|---|
| Home | Swipe up | App list |
| Home | Swipe right | Behaviour insights (when enabled) |
| Home | Swipe down | Notification shade |
| Home | Long-press empty space | Settings |
| Home | Tap clock / date / weather | Alarms / calendar / weather app |
| Home | Long-press a shortcut or pinned app | Edit, move, remove |
| App list | Enter on keyboard | Open top result, or web search if nothing matches |
| App list | Pull down at the top / Back | Return home |
| Insights | Swipe left / Back | Return home |

## Permissions

Every permission is optional and asked for only when you use the feature.

| Permission | Used for |
|---|---|
| Usage access (`PACKAGE_USAGE_STATS`) | Screen time and behaviour insights |
| Notification access | Notification shield |
| Approximate location | Weather for where you are |
| Internet, network state | Fetching the weather; the Wi-Fi/mobile indicator |
| Expand status bar | Swipe down for notifications |
| Request delete packages | "Uninstall" in the long-press menu |

## Privacy

minime has no analytics, ads or accounts. The only network request is to [Open-Meteo](https://open-meteo.com) for the weather, sending your approximate coordinates (rounded to ~100 m). Screen time, insights and the shield's "held back" list are computed and stored only on your device.

## Build

Requirements: JDK 17 and the Android SDK (platform 35), or just Android Studio.

**Android Studio:** File → Open → this folder, then Run.

**Command line:** point `local.properties` at your SDK (`sdk.dir=/path/to/Android/Sdk`), then:

```bash
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Release builds are signed with the debug key so they can be sideloaded; use your own signing config before distributing.

To build and install on a USB-connected phone (USB debugging on), including from WSL:

```bash
./install.sh
```

Then press Home and choose **minime**.

## Project layout

```
app/src/main/java/app/minime/launcher/
  MainActivity.kt           single activity, HOME intent filter
  LauncherViewModel.kt      app list, favorites, shortcuts, weather, screen time, insights, navigation
  Util.kt                   intents (alarms, calendar, weather, settings pages, notification shade)
  data/
    AppRepository.kt        launchable apps via LauncherApps, live updates, work profile
    Prefs.kt                settings (SharedPreferences)
    Search.kt               ranked, accent-insensitive search
    Shortcut.kt             shortcut model and default app hints
    UsageRepository.kt      foreground sessions and unlocks from usage events
    BehaviorAnalyzer.kt     daily/hourly stats and pattern rules for insights
    WeatherRepository.kt    location + Open-Meteo, cached
  shield/                   notification listener and held-back log
  ui/                       Compose screens and widgets
```

## Contributing

Issues and pull requests are welcome. Please keep the spirit of the project: calm, text-first, private by default, and no new permissions without a clear reason.

## License

[MIT](LICENSE)
