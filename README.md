# Kaizen

Kaizen is a personal planner for Android and iOS. It opens straight to the dashboard. To-dos, notes, goals, and history are saved on the device by default, so they work offline and survive closing the app. A different installation or browser profile starts with its own planner. Clearing app or browser storage removes local data.

## Install Kaizen on Android

Download `Kaizen-0.2.0.apk` from the [Kaizen Android release](https://github.com/23-prabhat/kaizen/releases/latest) on a device running Android 7.0 or later. Open the APK and follow Android’s install prompts. Install it over the signed 0.1.0 release to bring your existing planner and preferences forward. A fresh install opens an empty, offline planner without an account. Open the bell on the dashboard to enable reminders.

## Build the Kotlin Android app

The Android release lives in `native-android/` and uses Kotlin and Jetpack Compose. With JDK 17 or newer and Android SDK 37 installed, run:

```sh
cd native-android
./gradlew :app:assembleDebug
```

The debug APK is under `app/build/outputs/apk/debug/`. Use a separate emulator or test device for the debug build. Use the release signing key with the release APK when distributing an update.

## Run the Expo app locally

```sh
npm ci
npm start
```

Use `npm run android` for the Expo Android preview or `npm run ios` for iOS. Android requires the Android SDK. iOS requires Xcode on macOS. Rebuild existing Expo installs to register the home screen widget.

- **Private desktop preview:** Run `npm run demo -- --web --port 8082` and open [localhost:8082](http://localhost:8082/). If the private November report file is present, the command seeds it into this browser's planner once. It only listens on loopback.
- **Phone preview:** Run `EXPO_NO_DOTENV=1 npm run web -- --lan --port 8083`, then open `http://<computer-lan-ip>:8083` on a phone on the same Wi-Fi. The phone gets its own planner. Copy your private report file to the phone and choose it under **Settings → Planner data → Import report**. The app reads the file on the device and refreshes Dashboard and History.
- **Native reminders:** On Android or iOS, open the dashboard bell and tap **Manage reminders** to enable pending to-do and goal deadline notifications. The app schedules them on the device in local time.
- **Home screen widget:** In the rebuilt native app, tap **Add widget** on the dashboard’s **Daily motivation** card for instructions. The phone’s widget picker lists **Daily motivation** under Kaizen. Android refresh timing depends on the launcher, and opening Kaizen refreshes it immediately. Kaizen schedules the next 60 days of quotes for the iOS widget whenever you open the app.
- **Dashboard avatar:** **Rotate avatars** takes turns through the four built-in mascots and up to four local photos. **Use one image** keeps the selected photo in place. Tap the pencil on the dashboard to open these settings.

A Kaizen report is a JSON array of `{ id, day, title, note, done }` entries. Dates use `YYYY-MM-DD`, notes are text or null, and `done` is a boolean. Reimporting the same entries skips ones already saved and preserves edits. Keep personal reports out of version control and distributed builds.

## Optional Supabase mode for Expo

1. Set `EXPO_PUBLIC_DAYTABS_STORAGE=supabase` alongside your Supabase URL and publishable key in `.env.local` or the environment. The default is local storage, even if credentials are present.
2. Apply the [initial planner migration](supabase/migrations/20261112000000_create_planner.sql) and then the [flexible goals migration](supabase/migrations/20261112010000_add_flexible_goals.sql) if they aren't already applied.
3. Enable anonymous sign-ins in Supabase Auth and restart Expo. The app creates or restores a private guest session in the background. Row Level Security restricts each guest to their own rows.

Supabase mode uses a separate dataset. To reopen existing guest data, launch that mode on the same browser origin or app installation that holds its session. It does not sync or merge data from device mode.

Run `npm run typecheck` to check TypeScript. See [plan.md](plan.md) for behavior and acceptance checks.

The dashboard mascots were made with the built-in image generation tool. The assets and prompts are in [the asset notes](assets/desk-cat.md).
