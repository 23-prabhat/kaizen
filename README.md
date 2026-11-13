# Kaizen

Kaizen is an offline-first personal planner for organizing daily tasks, tracking goals, and reviewing past work. The native Android app is built with Kotlin and Jetpack Compose. It stores your planner on the device and opens directly to the dashboard without an account.

[Download the Android app](https://github.com/23-prabhat/kaizen/releases/latest)

## Features

- **Daily planning** — Plan today, tomorrow, or a later date. Set a deadline using hours and minutes or a clock time, and import a month’s tasks from Excel, CSV, or JSON.
- **Goals and progress** — Set a date range or duration in days, add an optional Short term or Long term tag, and keep a dated progress note for each day.
- **Dashboard and history** — See today’s remaining and completed to-dos, active goals, and a 14-day activity chart. Use History to revisit earlier tasks and their notes.
- **Local notifications** — Manage pending to-do, to-do halfway and deadline, goal deadline, and daily goal progress reminders independently under **Settings → Notifications**. Android requests notification permission when needed. If available, **Allow precise deadline alerts** enables exact timing for to-do deadlines.
- **Personalization** — Choose a light, dark, or system theme. Use a fixed avatar or rotate through the built-in mascots and up to four of your own photos. The dashboard shows a daily quote. The **Today’s to-dos** home-screen widget displays a scrollable list of that day’s tasks.

## Install on Android

1. Open the [latest GitHub release](https://github.com/23-prabhat/kaizen/releases/latest) on a device running Android 7.0 or later.
2. Download the `.apk` file under **Assets**, open it, and follow Android’s install prompts.
3. Install over an existing signed GitHub release to keep your planner and preferences. A fresh installation starts with an empty planner.

## Development

### Native Android

Open [`native-android/`](native-android/) in Android Studio, or use the Gradle wrapper from the terminal with JDK 17 or newer and Android SDK 37 installed.

```sh
cd native-android
./gradlew :app:assembleDebug
```

The debug APK is generated in `app/build/outputs/apk/debug/`. Run `./gradlew :app:installDebug` to install it on a connected test device or emulator. Run `./gradlew :app:lintRelease` to check the release build. Use the original signing key when distributing an update to the GitHub APK.

### Expo development and previews

The [`src/`](src/) directory contains the Expo implementation for iOS, web, and the Expo Android preview. Use Node.js 22.13 or later within the 22.x series.

```sh
npm ci
npm start
```

| Command | Purpose |
| --- | --- |
| `npm run web` | Start the browser preview |
| `npm run ios` | Build the iOS app with Xcode on macOS |
| `npm run android` | Build the Expo Android preview with the Android SDK |
| `npm run typecheck` | Check the TypeScript code |

For a browser preview on another device on the same Wi-Fi, run `EXPO_NO_DOTENV=1 npm run web -- --lan --port 8083` and open the LAN URL on that device. Each browser profile has its own planner. For a loopback-only development preview, run `npm run demo -- --web --port 8082`.

## Planner data

Kaizen saves tasks, goals, and preferences in the app’s local storage. Fresh installations start empty. Android can restore your existing planner from a device backup.

### Import to-dos in bulk

In the Android app, go to **Settings → Planner data → Import Excel or CSV**. Use a CSV file or the first worksheet of an Excel `.xlsx` workbook with these headers.

```csv
date,todo work,deadline
2026-11-16,Solve questions 1–10,4hr
2026-11-17,Solve questions 11–20,1hr 30 min
2026-11-18,Solve questions 21–30,30 min
2026-11-19,Review missed questions,5:30 PM
2026-11-20,Organize notes,
```

Use `YYYY-MM-DD` dates and leave the deadline blank for an all-day task. Excel date and time cells are also supported. Each file can contain up to 5,000 to-dos and must be under 2 MB. The app checks the whole file before saving and skips matching rows on reimport.

Durations must be at least one minute, with up to 23 hours and 59 minutes, and must end before midnight on the task’s date. For longer plans, create a goal. A duration counts from local midnight for tasks planned ahead and from the time you save or import a task for today. For past tasks imported into History, durations count from midnight on the recorded date.

With **To-do deadlines** enabled in **Settings → Notifications**, timed to-dos can trigger a halfway reminder and a deadline reminder while unfinished. For tasks planned in advance, the time window begins at local midnight on the planned day. For deadlines added on the day itself, it begins when the task is saved or imported.

### Import a Kaizen JSON report

Use **Settings → Planner data → Import JSON report** on Android. JSON files under 1 MB can use the same field names and deadline formats as a spreadsheet.

```json
[
  { "date": "2026-11-16", "todo work": "Solve questions 1–10", "deadline": "1hr 30 min" }
]
```

Android also supports existing Kaizen report files with `id`, `day`, `title`, `note`, and `done`. The `deadline` field is optional on Android. Use the existing Kaizen report format for **Import report** in Expo.

```json
[
  {
    "id": "weekly-review",
    "day": "2026-01-05",
    "title": "Review the weekly plan",
    "note": "Record the next action for each goal.",
    "done": true
  }
]
```

Reimporting the same report preserves existing edits. Keep personal reports out of version control.

## Project references

- [Original product plan](plan.md)
- [Mascot artwork and asset notes](assets/desk-cat.md)
