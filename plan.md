# To-do app plan

## First release

Build a personal planner with a native Kotlin Android release and an Expo implementation for iOS and web. Open the Dashboard immediately and save data on the device. A user can add a to-do with an optional note, track goals with flexible dates, and look up what they did on an earlier date.

### Screens and actions

| Screen | What users can do |
| --- | --- |
| **Dashboard** | See today’s remaining and completed to-dos, a 14-day activity chart, and active goals. Open the notification list from the bell to see unfinished to-dos and goal deadlines for today and tomorrow. |
| **To-dos** | See today's date and a count such as “2 of 5 done.” Tap **Add to-do** to enter a title and optional multiline note. Tap a checkbox to complete or reopen a to-do. Tap the row to read the full note, edit the to-do, or delete it after confirmation. Show open items first and keep completed items accessible below. |
| **Goals** | Add, edit, complete, reopen, or delete goals. Pick a start and finish date or enter a number of days from the start date. Review active, upcoming, and past goals in the same list. |
| **History** | Open on yesterday. Jump to a recent logged day, step between dates, or open the calendar for older entries. Show completed and unfinished to-dos for the selected date, with full notes available when a row is opened. Show “No to-dos for this day” for empty dates. |
| **Settings** | Switch between **Light**, **Dark**, and **System** appearance. Show where the planner is saved and let device planners import dated reports. |

Use labeled bottom tabs for Dashboard, To-dos, Goals, History, and Settings. Reuse the editor for adding and editing. Keep the title field first, with the optional note or goal schedule below and a visible save action above the keyboard.

### Proposed defaults

- Start with one offline planner per app installation or browser profile, with no sign-in. A fresh installation starts empty. The localhost preview can load a private report once, and a user can select their own report file to import on another device.
- Base today on the device's local calendar date. Save to-do dates and goal dates as calendar dates so they stay assigned to the same days when the device time zone changes.
- Keep both finished and unfinished to-dos on their original dates when midnight passes. History is read-only. Refresh today’s lists at local midnight and on app resume. If a to-do editor is already open at midnight, save to the date displayed in the editor.
- Count the start date as day one for goal duration. A goal is active from the start date through the finish date. The user marks each goal complete.
- Schedule a reminder at 7 PM if today’s to-do list has unfinished items. Remind users about unfinished goals at 9 AM the day before and on the finish date. Reminder times use the device’s local time. The app asks for notification permission when the user turns on a reminder.
- Start in Light mode and persist the chosen appearance on the device. Keep editor input on save failure and give users a retry action.

## Visual direction

Use a retro neo-brutalist planner layout. Take the yellow and lavender, heavy outlines, and folder shapes from the [Tuckii reference](https://in.pinterest.com/pin/946811521680804224/), and use [MyMoney’s](https://play.google.com/store/apps/details?id=com.raha.app.mymoney.free) compact dated lists as inspiration for History. The dashboard uses an original anime cat sticker, a purple desk cover with a hard offset shadow, and color-coded task cards. Keep list and editor screens quieter so titles and notes are easy to scan.

| Color role | Light | Dark |
| --- | --- | --- |
| Background | `#F5F1FF` | `#221D22` |
| List and editor surface | `#FFFEFC` | `#302830` |
| Text and control outlines | `#231A2A` | `#FFF6EE` |
| Primary action and selected tab | `#FFD947` | `#FFD947` |
| Remaining to-dos | `#FF987A` | `#FF987A` |
| Completed to-dos | `#BCA0FA` | `#C8ABFF` |
| Goal summary | `#D9F398` | `#D9F398` |

Use dark ink on accent fills in both modes. Use Bricolage Grotesque for headings and the platform font for body copy. Keep the selected navigation indicator inside its tab. Support larger text, labeled controls, and completion indicators that don't rely on color alone.

## Technical plan

- Use Expo Router for tab navigation and the editor modal. Share date calculations, themed components, and the editor between screens. [Expo Router documentation](https://docs.expo.dev/router/introduction/)
- Use app-private storage for planner entries and preferences. Use Android’s document picker for Excel, CSV, and report imports.
- Keep each to-do attached to its planned calendar date. Store optional deadline timestamps and calculate halfway alerts from the start of the available time window.
- Use the Android widget collection for the current day’s list, including both open and completed items.

## Build order

1. **Foundation.** Scaffold Expo and TypeScript. Add tab navigation, theme tokens, and appearance persistence. Add device persistence.
2. **Daily checklist.** Build Today, the to-do editor, notes, completion, and deletion. Handle validation, loading, empty lists, save failures, and retry.
3. **History.** Add day navigation and the calendar picker. Verify that rollover and a time-zone change preserve previously saved lists and notes.
4. **Goals and Dashboard.** Build flexible date and duration scheduling, goal editing, and the overview of today’s to-dos and active goals.
5. **Device testing.** Run the full flow on Android and iOS and check light and dark appearance, large text, screen reader navigation, and interrupted saves.

## Acceptance checks

- Add two to-dos today, one with a multiline note. Complete one, restart the app, and verify that both items, their statuses, and the note are saved.
- After midnight, the Dashboard and To-dos show the new date. History opens on yesterday and shows both items and the original note. Jumping to an empty day displays the empty state.
- Create a goal by selecting dates and another by entering a duration. A one-day goal starts and finishes on the same date. Completing an active goal updates the Dashboard.
- Switch appearance and restart. The selected mode persists. System mode responds to a device theme change.
- Enable reminders from the dashboard bell on a native device, then complete all of today’s to-dos and a goal with a scheduled reminder. Verify their alerts are canceled. Check that another unfinished goal can still produce a deadline alert.
- Interrupt a save and retry. The input stays available and a successful retry results in a single row.
- Open the app in a fresh browser profile or on another device. It opens without signing in and cannot see the first planner.
- Import the November report and verify seven completed entries, two remaining tasks, and eight logged dates in the 14-day window ending November 12. Import it again and verify no duplicates.
