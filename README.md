# GoatKeeper Android

GoatKeeper is a local-first goat farm management app for Android Studio. It is a native Kotlin/Jetpack Compose conversion of the supplied prototype.

## Open and run

1. Open the `GoatKeeper-Android` folder in Android Studio (Ladybug or newer recommended).
2. Let Android Studio complete the Gradle sync.
3. Choose an Android 8.0+ device or emulator and select **Run**.

The first Gradle sync downloads standard Android libraries. No account, Thunkable subscription, or Internet connection is needed after installation to use the farm registry.

## Included

- Herd registry with ID, name, breed, date of birth, sex, parent IDs, and status
- Goat profile and chronological history
- Health, breeding, kidding, insurance, sale, and transfer entries
- Automatic expected kidding date (mating date + 150 days)
- Dashboard statistics and due/overdue alert list
- Local Room database — records remain available offline
- Searchable herd list and shareable herd and sales summaries

## Deliberately local in version 1

The original prototype was local-only. This Android version preserves that choice so it remains fully usable without a subscription or connection. Photo capture, document attachments, phone-calendar events, scheduled notifications, cloud backup, PDF output, and Excel `.xlsx` output are planned extension points; their final behavior requires choices such as Firebase project ownership, document storage provider, and the farm's notification policy.

## Adding Firebase later

To add optional cross-device synchronization and Firebase Cloud Messaging, create a Firebase project, add `app/google-services.json`, then add the Firebase BoM plus Firestore/Auth/Messaging dependencies to `app/build.gradle.kts`. Keep Room as the source of truth and sync changes in the background when connectivity returns.

## Project structure

- `MainActivity.kt` — Android entry point and Android share sheet
- `GoatKeeperApp.kt` — Compose screens and data-entry dialogs
- `data/FarmDatabase.kt` — Room entities, queries, and local database
