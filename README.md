# Sortr

An Android app for cleaning up your photo gallery by swiping, with photos grouped by the people
in them. Everything runs on the phone. The app has no internet permission, no analytics and no
cloud services.

| Swipe | Meaning |
|---|---|
| Right | **Keep.** Won't show up again unless you reset reviewed photos. |
| Left | **Delete.** Goes to Sortr's Trash. Nothing leaves the phone until you empty the Trash and confirm. |
| Up | **Private.** Moves the photo into the encrypted vault *(Phase 2)*. |
| Down | **Skip.** No decision. It comes back in the next session. |

## Status

| Phase | Contents | State |
|---|---|---|
| 1 | Setup, permissions, gallery loading, swipe (Keep/Delete/Skip), Trash, undo | Done |
| 2 | Private vault: encryption, fingerprint, PIN, restore, backup | Done |
| 3 | Offline face grouping, People grid, person sessions | Not started |
| 4 | Duplicates, blurry photos, screenshots, year/month sessions, albums (merge, sort), stats | Not started |
| 5 | Polish, performance on large galleries | Not started |

## Building

The build uses the toolchain in `E:\android-toolchain` (JDK 17, Android SDK, Gradle 8.10.2).
Nothing is installed to `C:`. Gradle's download cache lives in `E:\android-toolchain\gradle-home`.

In PowerShell, from the project folder:

```powershell
. .\scripts\env.ps1                     # sets JAVA_HOME, ANDROID_HOME, GRADLE_USER_HOME, PATH
gradle :app:assembleDebug               # builds app\build\outputs\apk\debug\app-debug.apk
gradle :domain:test :app:testDebugUnitTest   # unit tests
```

`local.properties` (not committed) points at the SDK: `sdk.dir=E\:\\android-toolchain\\sdk`.

`gradlew.bat` also works, but it downloads its own copy of Gradle 8.10.2 the first time.

### Versions

Gradle is pinned to 8.10.2, which limits the Android Gradle Plugin to 8.8.x. That supports up
to API 35, so the app uses `compileSdk 35` / `targetSdk 35` with `minSdk 30` (Android 11).
This is fine for installing on your own phone. Publishing on Google Play would need a newer
Gradle and target SDK.

### Offline guarantee

`app/src/main/AndroidManifest.xml` strips `INTERNET`, `ACCESS_NETWORK_STATE` and
`ACCESS_WIFI_STATE`, even if a library tries to add them. The build task
`verify<Variant>NoNetworkPermissions` fails the build if any network permission reaches the
final manifest. The list of permissions that remain is written to
`app/build/reports/permissions/<variant>.txt`.

App data is also excluded from Android's cloud backup and device-to-device transfer.

## Installing on your phone

1. On the phone, open **Settings → About phone** and tap **Build number** seven times to turn on
   developer options.
2. Open **Settings → System → Developer options** and turn on **USB debugging**.
3. Connect the phone by USB and accept the "Allow USB debugging?" prompt.
4. In PowerShell:

   ```powershell
   . .\scripts\env.ps1
   adb devices                          # your phone should be listed as "device"
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

Without a cable: copy `app-debug.apk` to the phone, open it in the Files app, and allow
"Install unknown apps" for the Files app when asked.

## Private vault

Swiping up moves a photo into an encrypted vault inside the app.

- **First use.** The first swipe up asks you to set up the vault. Setup warns that uninstalling
  Sortr deletes the vault, then asks for a 4 to 12 digit PIN.
- **Encryption.** Each photo gets its own random AES-256 key and is encrypted with AES-GCM
  into the app's private storage (`no_backup/vault/`). The per-photo key is itself encrypted
  by a master key held in the Android Keystore, which can't be copied off the phone. A small
  encrypted thumbnail is stored too, so the grid opens quickly.
- **Verification before removal.** After encrypting, Sortr decrypts the copy and compares
  SHA-256 hashes. Only verified photos are offered for removal from the gallery, in one batch
  behind Android's confirmation dialog. If you tap Deny, the originals stay where they are and a
  reminder appears on Home.
- **Unlocking.** Fingerprint (strong biometrics only), with the PIN as a fallback. The PIN
  is stored only as an HMAC made with another Keystore key. After 5 wrong PINs there is a wait,
  starting at 30 seconds and doubling up to 15 minutes.
- **Privacy on screen.** Vault screens block screenshots and screen recording, and Sortr's
  preview in the recent apps list goes blank. The vault locks whenever Sortr leaves the screen.
- **Memory only.** Photos are decrypted into memory for viewing, through a separate image
  loader with no disk cache, which is cleared when the vault locks. Nothing decrypted is
  written to disk.
- **Restore** saves the photo to `Pictures/Sortr` and removes it from the vault. **Delete**
  removes it permanently, after a confirmation.
- **Undo** works on a swipe up until the original has been removed from the gallery.

### Backups

Settings has **Export vault backup** and **Import vault backup**. Both ask for the vault PIN
or fingerprint first.

- Because the Keystore key can't leave the phone, a backup is encrypted with a separate backup
  password you choose (at least 8 characters). The key is derived with PBKDF2-HMAC-SHA256 at
  600,000 iterations.
- File format: a `SORTRBAK` header, then AES-GCM chunks. Each chunk is authenticated together
  with the header and its position, so tampering, reordering or truncation is detected.
- Import skips photos already in the vault, matched by SHA-256. A wrong password, a damaged
  file and a file that isn't a backup each get their own message.
- **There is no way to recover a backup if the password is forgotten.**

## Face model (Phase 3)

Face grouping needs a MobileFaceNet model that you supply. It is not committed to git.

- Location: `app/src/main/assets/models/mobilefacenet.tflite`
- Expected input: one 112×112 RGB face, float32, shape `[1, 112, 112, 3]`, pixel values scaled
  as `(value − 127.5) / 128`.
- Expected output: one embedding, shape `[1, 128]` or `[1, 192]`.

The code reads the real input and output shapes from the file when it loads it. If the file is
missing, the app still works with face grouping turned off and says so. These details will be
confirmed against your file in Phase 3.

## Project layout

```
domain/   Plain Kotlin: models, repository interfaces, swipe session logic. No Android code.
data/     Room database, MediaStore access, gallery sync, settings (DataStore).
app/      Compose UI, ViewModels, navigation, dependency injection (Hilt).
scripts/  env.ps1 for the local toolchain.
```

The database schema is exported to `data/schemas/` so future changes can be migrated without
losing data.

## How it works

- **Gallery sync.** On launch, on resume, and whenever the gallery changes, Sortr compares
  MediaStore with its database. It adds new photos, updates edited ones, and removes rows for
  photos deleted outside the app. It never touches the database when it has no photo access,
  so revoking permission can't wipe your progress.
- **Sessions.** A session is a snapshot: unreviewed photos first, then previously skipped
  ones, newest first. Each decision is saved immediately, so leaving halfway loses nothing.
- **Undo.** The last 50 decisions of the current session can be undone, one at a time.
- **Deleting.** Empty Trash asks Android for one confirmation covering every photo. If you
  tap Deny, nothing is deleted and the photos stay in the Trash. Sortr can't delete a photo
  without that system confirmation.

## Known limitations

- Images only; videos are ignored for now.
- Undo history covers the current session only. Your decisions are kept when you leave a
  session, but they can no longer be undone from the swipe screen. Kept and skipped photos can
  be reset in Settings, and deleted ones restored from the Trash.
- The fingerprint and PIN guard the vault screens, but they aren't part of the encryption key.
  This is so photos can be encrypted while you swipe, without a prompt each time. The vault
  protects against someone who picks up your unlocked phone. It doesn't protect against an
  attacker who can run their own code on a rooted phone.
- Vault photo names and dates are stored unencrypted in the app's private database. The photos
  and thumbnails themselves are encrypted.
- Restored vault photos go to `Pictures/Sortr`, not the album they came from.
- Opening the file picker for a backup sends Sortr to the background, so the vault locks.
  That's expected: you'll be asked for the PIN again next time.
- Photos restored from the Trash come back as unreviewed.
- With "Select photos" access (Android 14+), Sortr only knows about the photos you selected.
  If you later deselect a photo, Sortr forgets whether you had reviewed it.
- In the full-screen view, a zoomed photo can be panned into the black border around it.
- Uninstalling Sortr deletes its review history and the vault. Export a vault backup first.
- Debug builds are about 60 MB because unused code isn't stripped. Release builds are about 4 MB.
