# Sortr

An Android app for cleaning up your photo and video gallery by swiping, with photos grouped by
the people in them, similar and blurry photos found for you, albums you can merge, rename and
split by year, WhatsApp's hidden photos and videos included, exact duplicates, smart categories
and text search, a daily goal and a home screen widget. Everything runs on the phone. The app has no internet permission, no analytics and no
cloud services.

| Swipe | Meaning |
|---|---|
| Right | **Keep.** Won't show up again unless you reset reviewed photos. |
| Left | **Delete.** Goes to Sortr's Trash. Nothing leaves the phone until you empty the Trash and confirm. |
| Up | **Private.** Moves the photo into the encrypted vault. Photos only, not videos. |
| Down | **Skip.** No decision. It comes back in the next session. |

## Status

| Phase | Contents | State |
|---|---|---|
| 1 | Setup, permissions, gallery loading, swipe (Keep/Delete/Skip), Trash, undo | Done |
| 2 | Private vault: encryption, fingerprint, PIN, restore, backup | Done |
| 3 | Offline face grouping, People grid, person sessions | Done |
| 4 | Videos, similar and blurry photos, sessions by year/month/album/app/size, albums (merge, rename, move), custom sessions, stats, new look | Done |
| 5 | Apple-style look everywhere, faster face pictures, big-gallery tuning, swipe-down viewer, restore to original album, Arabic-ready layout | Done |
| Extras 2 | Screenshots page and tidy-up, On this day, daily goal with streak and reminder, home screen widget, Arabic translation, exact duplicates (photos and videos), Recently sorted with Undo, save/restore sorting progress, share into the vault, smart categories, text search | Done |
| Extras | WhatsApp's hidden media and a WhatsApp page, cleaner Home, hold and drag to select, Share everywhere, organize by year, moves that copy when Android refuses, View/Delete/Share/Move for faces and people, "Try again" for unsorted faces, photo checks on several cores, purple icon again | Done |

## Building

The build uses the toolchain in `E:\android-toolchain` (JDK 17, Android SDK, Gradle 8.10.2).
Nothing is installed to `C:`. Gradle's download cache lives in `E:\android-toolchain\gradle-home`.

In PowerShell, from the project folder:

```powershell
. .\scripts\env.ps1                     # sets JAVA_HOME, ANDROID_HOME, GRADLE_USER_HOME, PATH
gradle :app:assembleRelease             # builds app\build\outputs\apk\release\app-*-release.apk
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

ML Kit bundles Google's usage-logging library (`datatransport`). Without `INTERNET` it can't send
anything. On top of that, the manifest removes its upload backend (`cct`), so it drops events
at once instead of storing them and scheduling upload jobs; the log shows "Transport backend
'cct' is not registered". The same build task fails if an upload backend comes back.

Image labelling and text recognition (for categories and search) use ML Kit's *bundled* models,
which ship inside the app; nothing is downloaded at runtime.

App data is also excluded from Android's cloud backup and device-to-device transfer.

The permissions that remain are photo and video access, fingerprint, notifications and
foreground service (both for the background scan), plus WorkManager's wake lock and restart
after reboot.

## Installing on your phone

1. On the phone, open **Settings → About phone** and tap **Build number** seven times to turn on
   developer options.
2. Open **Settings → System → Developer options** and turn on **USB debugging**.
3. Connect the phone by USB and accept the "Allow USB debugging?" prompt.
4. In PowerShell:

   ```powershell
   . .\scripts\env.ps1
   gradle :app:assembleRelease
   adb devices                          # your phone should be listed as "device"
   adb install -r app\build\outputs\apk\release\app-arm64-v8a-release.apk
   ```

The build makes one APK per processor type, because ML Kit's native code is large. Almost every
phone from the last several years takes `arm64-v8a` (about 52 MB). If Android says the app isn't
compatible, use `app-universal-release.apk`, which works everywhere but is about twice the size.
Release builds are signed with the same local key as debug builds, so they install over each
other and keep your data.

Without a cable: copy the APK to the phone, open it in the Files app, and allow "Install unknown
apps" for the Files app when asked.

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
- **Restore** puts the photo back in the album it came from (or `Pictures/Sortr` when Android
  doesn't allow that folder, or for photos imported from a backup) and removes it from the
  vault. If the original never left the gallery, it simply stops being private; no second copy
  is made. **Delete** removes it permanently, after a confirmation.
- **Undo** works on a swipe up until the original has been removed from the gallery.
- **Sharing in.** In any app's share sheet, **Sortr vault** encrypts the shared photos straight
  into the vault (once the vault is set up). Only content addresses from other apps are
  accepted, photos already in the vault are skipped, and the original stays in the app it came
  from, since Sortr can't delete other apps' files.

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

## Face model

Face grouping needs a MobileFaceNet model that you supply. It is not committed to git.

- **File:** `app/src/main/assets/models/mobilefacenet.tflite` (exactly this name).
- **Input:** float32 tensor `[N, S, S, 3]`, RGB, values scaled as `(value − 127.5) / 128`
  (about −1 to 1). A uint8 `[N, S, S, 3]` input also works. S is read from the file and is
  usually 112. With N > 1, only the first slot is used.
- **Output:** float32 `[N, D]`, D between 64 and 2048 (usually 128 or 192). Sortr
  L2-normalises it.
- **Tested with:** the 5,233,552-byte `mobilefacenet.tflite` from
  [MCarlomagno/FaceRecognitionAuth](https://github.com/MCarlomagno/FaceRecognitionAuth)
  (input `[1, 112, 112, 3]`, output `[1, 192]`). The log line
  `Face model loaded: input 112×112, embedding 192` confirms it was read.

If the file is missing, or its shapes don't fit the rules above, the app still works with face
grouping turned off. Home and Settings explain why.

## Face grouping

- **Scanning** runs as a WorkManager job, after the similar/blurry check, in batches of 16
  photos, newest first. It shows a progress notification and also appears on Home as "Finding
  people… x of y". Progress is
  saved after every batch, so the job carries on where it stopped after being interrupted, and
  later runs only scan new or edited photos. There's an optional "Scan only while charging"
  setting, and the job always waits if the battery is low.
- **Face pictures** on the People screens are cut straight from the photo file: only the square
  around the face is decoded, then kept as a small JPEG (320 px) in the app's private cache, so
  later visits are instant. The cache is capped at 100 MB. Pictures of a photo are deleted as
  soon as it goes into the vault, and the background job removes pictures of faces that can no
  longer be shown (Trash, vault, deleted or edited photos).
- **Per photo:** decode a copy at most 1280 px on its longest side (EXIF rotation applied),
  detect faces with ML Kit's bundled model (landmarks on), drop duplicate detections of the
  same face, and skip faces under 80 px or with the head turned too far (yaw over 40°, pitch
  over 30°, roll over 45°). Each face is rotated and scaled so the eyes land on the standard
  112×112 ArcFace positions, then embedded with MobileFaceNet.
- **Grouping:** each face is compared with every person's average embedding by cosine
  similarity. At or above the threshold (0.60 by default, set by "Grouping strictness" in
  Settings) it joins the closest person, whose average is updated. Within 0.08 below the
  threshold it goes to Unsorted faces instead of being guessed. Clearly below, a clear face
  starts a new person. Small or turned faces never start a new person.
- **People** show up once they have a name or at least two faces. One-off faces of unnamed
  people are listed under Unsorted faces.
- **Editing:** rename, merge (from the person page, or long-press several people in the grid),
  hide, "Not them" for wrong faces (they're never matched to that person again), and
  grouping faces from Unsorted into a new or existing person. People you've edited are kept as
  they are when the strictness changes; everyone else is regrouped from the stored embeddings,
  without scanning photos again. **Rescan all faces** starts over from scratch.
- **Privacy:** faces in photos that are in the vault or the Trash are never shown or counted,
  and those photos aren't scanned.

## Browsing and cleaning up

Home starts with a card showing how much is left to sort, how much is done, and a **Start
sorting** button. A short to-do list appears under it only when something needs you: allowing
full photo or video access, removing vault originals from the gallery, or including WhatsApp's
hidden media (this one can be dismissed). Then, when there's something to show, today's
**daily goal** with your streak, and **On this day**: photos taken on today's date in earlier
years, as their own session. Below that:

- **Browse.** Tiles with a count each:
  - **By date:** years, each opening into months. Every year and month can be sorted on its own.
  - **Albums:** every folder with photos or videos.
  - **Duplicates:** exact copies of the same photo or video.
  - **Similar:** groups of near-identical photos.
  - **Blurry:** photos that look out of focus.
  - **Videos.**
  - **Large files:** 5 MB and over, largest first.
  - **Categories:** documents, receipts, memes, food and pets.
  - **Search:** find photos by the words in them.
- **Custom session.** Mix a type (photos or videos), a year and month, any albums and a
  minimum size. The button shows how many items match before you start.
- **People.** The people with the most photos, and a shortcut to Unsorted faces.
- **From apps.** Camera, Screenshots, Screen recordings, WhatsApp, Snapchat, Telegram,
  Instagram, Messenger/Facebook, TikTok and Downloads, recognised by their folder names. Only
  the ones you have show up. Tapping one starts a session; WhatsApp and Screenshots open their
  own pages.
- **Library.** Trash, the private vault, **Recent** (decisions you can undo) and **Stats**: how
  much you've sorted, space freed, every kind of decision, and photos per year.

### Exact duplicates

A photo or video saved twice (from WhatsApp, say) is byte-for-byte the same file. To find those
without reading the whole library, Sortr first looks for files with exactly the same size, and
only fingerprints those: SHA-256 of the whole file up to 32 MB, or of the size and three 1 MB
samples (start, middle, end) for bigger files. Files with the same size and fingerprint form a
group. The copy to keep is the one you already kept, else one the gallery can see, else one in
the camera folder, else the oldest; each copy is labelled with its album, and you can tap another
to keep it instead. **Keep one, trash N**, **Clean up all groups** and **Review one by one**
work like the Similar screen, with Undo.

### Screenshots

Screenshots are found by folder ("Screenshots" anywhere in the path) or by file name
("Screenshot…", "Screen shot…"), so ones saved in Downloads or elsewhere count too. The
Screenshots page has sessions for all of them and for those **older than 30 days**, and **Put
them all in "Screenshots"**: every screenshot outside the main screenshot album (the biggest one
Android lets apps move into, or `Pictures/Screenshots`) is moved there, after one confirmation.

### Categories and search

After the other checks, a background step reads each photo once with ML Kit's on-device image
labelling and text recognition (newest first, on a copy at most 1600 px wide):

- **Food** and **Pets** come from confident image labels.
- **Receipts** have receipt words (total, tax, cash…) and several prices; **Documents** have a
  lot of text; **Memes** are pictures with a few lines of text that came from a chat or social
  app. Screenshots are never put in these three.
- The text is kept in a full-text index, so **Search** finds photos by any word in them, as you
  type ("addr" finds "address"). Search results can be sorted as a session.
- Text read from a photo is deleted when the photo goes into the vault, and with the photo.

### Recently sorted

**Recent** in the Library lists every keep, delete, private and skip decision from the last 30
days, grouped by day, with a badge for what was decided and a filter. Select photos (tap, or
hold and drag) and tap **Undo** to put them back as they were, in any order and long after the
session. A photo only shows its latest decision, and only while that decision still holds.
Vault photos can be taken back only while their original is still in the gallery.

### Daily goal and reminder

**Settings → Daily goal** sets a goal of 25, 50, 100 or 200 a day. Keep, Delete and Private
count; skips don't. Home shows today's progress and how many days in a row you reached the goal
(the streak survives until the end of the day). An optional **Reminder** at a time you choose
sends one gentle notification, only if the goal isn't reached yet and there's something left to
sort; tapping it opens a session.

### Home screen widget

The **Left to sort** widget (2×2, resizable) shows how much is left, today's goal, and a
**Start** button that opens a session straight away. It updates while Sortr runs, and Android
refreshes it every 30 minutes otherwise.

### Saving your sorting progress

**Settings → Sorting progress → Save** writes a small JSON file with which photos you kept,
skipped or put in the Trash, matched by album, file name and size (gallery ids don't survive a
reinstall). **Restore** applies it to photos that are still waiting to be sorted; decisions you
already made on this phone win. The vault isn't part of this file; it has its own encrypted
backup.

### Similar and blurry photos

A background job checks every photo once, newest first, in batches of 48, working on 2 to 4
photos at a time (half the phone's cores). It shares the
notification and the charging setting with the face scan, and runs even without the face model.

- **Similar** uses a 64-bit difference hash (dHash) of a 9×8 greyscale thumbnail. Photos whose
  hashes differ in 6 bits or fewer are grouped. A BK-tree finds the matches without comparing
  every pair. Resized, recompressed, lightly edited and blurred copies all land in the same
  group.
- In each group the **best copy** is the one with the most pixels, then the sharpest, then the
  largest file, then the oldest. Tap another photo to keep that one instead. **Keep best, trash
  N** sends the others to the Trash, with Undo. **Keep all** marks the group as not duplicates.
  **Clean up all groups** does every group at once, after a confirmation.
- **Blurry** uses the variance of the Laplacian (an edge filter) on a copy at most 512 px wide.
  Below 60 counts as blurry.
- While checking, Sortr also reads the date stored in the photo itself. MediaStore leaves the
  date empty for photos whose camera didn't record a time zone, and those photos would
  otherwise be filed under the day they were added to the phone.

### Albums

- Sort the album list by most items, largest, name, most recent, or **by year** (albums grouped
  under the year of their newest item).
- **Merge:** tap Select (or hold an album and drag across others), choose two or more albums,
  tap Merge, and pick one of them or create a new album.
- In an album, photos are grouped by year. **Sort this album** starts a session. **Select** (or
  hold a photo and drag across others) opens a toolbar with **Share**, **Move to…** and
  **Delete** (to Sortr's Trash, with Undo). While selecting, each year has its own Select
  button. **⋯ → Rename** renames the album; **⋯ → Organize by year** moves everything into one
  album per year ("Trip 2023", "Trip 2024", …) in `Pictures/`.
- Android shows one "Allow Sortr to modify N photos?" dialog per move, covering up to 1,000
  items. Nothing moves if you tap Deny.
- A move normally only changes the folder. The photo, its review status, its faces and its
  similar/blurry results stay the same.
- Android refuses to move some files, most often ones that belong to another app's folder
  under `Android/media` (WhatsApp's). Sortr then copies them into the new album, keeping their
  date and review status, and asks once to remove the originals: Android's dialog for gallery
  items, Sortr's own for hidden WhatsApp files. If you say no, the copies are removed again, so
  nothing ends up twice. Copies get their faces and similar/blurry check redone in the
  background.
- Android only allows moving into folders under `DCIM/` or `Pictures/`. Other albums, like
  Downloads or WhatsApp's, can be moved *from* but not *into*, and the picker shows them greyed
  out. New albums go in `Pictures/`.

### Selecting and sharing

- Wherever you can select photos (albums, the album list, the Trash, a person's page, Unsorted
  faces), you can hold one and drag across others to select the whole run. Near the top or
  bottom edge the grid scrolls by itself. Starting on a selected photo unselects instead.
- **Share** is in every selection toolbar and in the full-screen viewer. It opens Android's
  share sheet and gives the chosen app temporary read access to just those items. Vault photos
  can't be shared, because that would mean writing a decrypted copy to disk.
- The full-screen viewer shows "3 / 12" at the top; swipe sideways for the next photo.
- On a person's page or in Unsorted faces, selected faces can be grouped (**Not them**, **Add
  to person**, **New person**), their photos moved with **Move to…** (an existing album or a new
  one), opened with **View**, shared, or sent to the Trash with **Delete** (with Undo).
- To move everything of a person: **⋯ → Move all photos to an album** on their page, or select
  one or more people in the People grid and tap **Move to…**. Moves work as in albums, including
  the copy fallback; photos in the Trash or the vault stay where they are.
- When a toolbar has more than five actions, the last ones are under **More**.
- **Try again** in Unsorted faces regroups them with the current strictness. Faces you've
  marked "Not them" stay away from that person.

### WhatsApp media

WhatsApp keeps its photos and videos in `Android/media/com.whatsapp/WhatsApp/Media`. Received
ones are in the gallery, but the `Sent` and `Private` folders contain a `.nomedia` file, so
Android hides them from every gallery app.

- **Including them.** Tap **Choose folder** on Home's "Sort all your WhatsApp media" row, or
  **Include hidden WhatsApp media** in Settings → WhatsApp. Android's folder picker opens straight on WhatsApp's Media folder; tap **Use this
  folder**, then **Allow**. WhatsApp Business works the same way. Sortr only gets access to that
  one folder, and you can remove it in Settings at any time.
- **What's added.** Images and videos in "WhatsApp Images" and "WhatsApp Video", including
  their Sent and Private folders. Voice notes, documents, stickers and backups are ignored. The
  folder is re-read with every gallery sync.
- **WhatsApp page.** WhatsApp under From apps opens a page with Photos, Videos, Sent and Private
  chats, each starting a session, plus the included folders.
- **Deleting hidden items.** Android's delete confirmation only accepts gallery items and
  refuses these files. So when the Trash holds hidden WhatsApp items, Sortr asks with its own
  dialog ("Delete N hidden WhatsApp items?") after Android's. Nothing is deleted without one of
  the two.
- Otherwise hidden items work like any others: sessions, Trash, vault, albums, faces, Share.

### Videos

Videos appear in sessions, albums and the date and app groupings, with a play symbol and their
length. Tapping a video card plays it full screen (tap to pause). You can keep, skip or trash
them; the vault is photos only for now. Videos aren't checked for similarity or faces.

## Look and feel

The design follows iOS: a light grey page with white rounded cards (black and dark grey in dark
mode), large titles that shrink into the bar as you scroll, one blue accent, grouped lists with
coloured icon badges, and round buttons on the swipe screen. Screens slide in from the side, and
Android's back swipe previews the screen underneath. In the full-screen viewer, drag a photo
down to close it. The app icon is a white check on purple. The building blocks live in
`app/src/main/kotlin/com/sortr/app/ui/design`.

## Arabic and right-to-left

Sortr is translated into Arabic (`app/src/main/res/values-ar/strings.xml`), with all six Arabic
plural forms. It follows the phone's language; on Android 13+ you can also pick Arabic for
Sortr alone in **Settings → Apps → Sortr → Language**. Layouts mirror: lists, arrows, tiles and
the vault screens flip, numbers and dates use Arabic-Indic digits, and the swipe buttons stay in
their physical left/right positions on purpose. Hashes and other values that must stay the same
in every language are always written with ASCII digits. (Large text uses no custom letter
spacing, because Android doesn't apply it to Arabic script.) New English text goes in
`values/strings.xml`, with its Arabic next to it in `values-ar`.

## Performance

Measured on the x86_64 emulator with 2,810 generated photos, plus a copy of the database inflated
to 51,000 photos and 60,000 faces:

| What | Result |
|---|---|
| Gallery sync, 2,810 new photos | 0.7 s |
| Similar/blurry check | about 100 photos per second (small images; 12 MP photos are slower) |
| Home, album, date and session queries at 51,000 photos | 1–55 ms each |
| People list at 60,000 faces / 400 people | 0.12 s |
| Similar groups at 2,800 photos | 287 groups, screen opens at once |
| Reading photos for categories and search | about 7 photos per second |

While photos are being checked, Home recounts similar photos at most every 3 seconds, and the
grouping reads only ids and hashes, loading full details just for photos that are in a group.

## Project layout

```
domain/   Plain Kotlin: models, repository interfaces, swipe sessions, face maths, alignment
          and grouping, perceptual hash, blur score, duplicate grouping, session filters, album
          rules, PIN rules. No Android code; unit-tested on the PC.
data/     Room database, MediaStore access (photos and videos), gallery sync, album moves,
          settings (DataStore), vault crypto and storage, photo analysis, face detection (ML Kit)
          and embedding (LiteRT).
app/      Compose UI (design system in ui/design), ViewModels, navigation, dependency injection
          (Hilt), background scan worker.
scripts/  env.ps1 for the local toolchain.
```

The database schema is exported to `data/schemas/` so future changes can be migrated without
losing data.

## How it works

- **Gallery sync.** On launch, on resume, and whenever the gallery changes, Sortr compares
  MediaStore's photos and videos with its database. It adds new items, updates edited ones,
  follows items moved to another album, and removes rows for items deleted outside the app. It
  never touches rows of a type (photos or videos) it currently has no access to, so revoking
  permission can't wipe your progress. Included WhatsApp folders are read right after; if one
  can't be read, its items are left alone rather than removed.
- **Sessions.** A session is a snapshot: unreviewed items first, then previously skipped ones,
  newest first (blurriest first for Blurry, largest first for Large files). Each decision is
  saved immediately, so leaving halfway loses nothing.
- **Undo.** The last 50 decisions of the current session can be undone, one at a time, and
  any decision from the last 30 days from **Recent**.
- **Background checks.** One job, in this order: similar/blurry check, exact-copy fingerprints,
  faces, then categories and text. It saves after every batch and carries on where it stopped.
- **Deleting.** Empty Trash asks Android for one confirmation covering every photo. If you
  tap Deny, nothing is deleted and the photos stay in the Trash. Sortr can't delete a gallery
  photo without that system confirmation. Hidden WhatsApp files are the one exception, since
  Android's dialog refuses them: they get Sortr's own confirmation instead.

## Known limitations

- Videos can't go in the vault yet, and aren't checked for similarity or faces.
- The blur check can't tell a blurry photo from a plain one: a clear sky, a white wall or a
  dark night shot can also show up under Blurry. Look before trashing.
- Similar photos are found by overall structure, so the same drawing in different colours, or
  photos of the same scene taken seconds apart, also count as similar. That's usually what you
  want for bursts, but check the group before cleaning up.
- The swipe screen's Undo covers the current session; older decisions are undone from Recent,
  which keeps 30 days.
- The fingerprint and PIN guard the vault screens, but they aren't part of the encryption key.
  This is so photos can be encrypted while you swipe, without a prompt each time. The vault
  protects against someone who picks up your unlocked phone. It doesn't protect against an
  attacker who can run their own code on a rooted phone.
- Vault photo names and dates are stored unencrypted in the app's private database. The photos
  and thumbnails themselves are encrypted.
- Vault photos imported from a backup are restored to `Pictures/Sortr`, since the backup doesn't
  record their album.
- Opening the file picker for a backup sends Sortr to the background, so the vault locks.
  That's expected: you'll be asked for the PIN again next time.
- Photos restored from the Trash come back as unreviewed.
- With "Select photos" access (Android 14+), Sortr only knows about the photos you selected.
  If you later deselect a photo, Sortr forgets whether you had reviewed it.
- Uninstalling Sortr deletes its review history and the vault. Export a vault backup first.
- Face grouping is only as good as the model. Very similar-looking people, babies, and photos
  years apart can be grouped wrongly or split. Use merge and "Not them" to correct it.
- Grouping is incremental, so the order photos are scanned in has a small effect on the result.
  Changing the strictness regroups everyone except people you've edited.
- The first visit to a page with many faces still decodes each face once; after that they come
  from the cache.
- Faces smaller than 80 px in the 1280 px scan copy (about 6% of the photo's width) are ignored,
  so people in the far background of group photos aren't grouped.
- ML Kit's native code makes the app large: about 52 MB for the arm64 release APK, 111 MB for the
  universal one, and debug builds bigger still.
- Categories are best guesses from a general image model and the text in a photo. Memes in
  particular are guessed from short text in pictures from chat apps.
- Text search finds text in Latin letters (English, French, Spanish…); ML Kit's bundled reader
  doesn't read Arabic script.
- Text read from photos is stored unencrypted in the app's private database, like the rest of
  the index. It's removed when a photo goes into the vault.
- The progress file is plain JSON with file names and decisions. Keep it somewhere private.
- When the app isn't running, the widget can be up to 30 minutes behind, and Android may send the
  reminder a little late to save battery.
- The progress bar counts photos in the Trash and the vault as sorted, until they're deleted or
  their originals removed; after that they're no longer part of the count.
- Android doesn't delete folders, so merging or renaming albums leaves empty folders behind
  (gallery apps don't show them).
- Hidden WhatsApp items are read through the folder permission, which is slower than the
  gallery, so a phone with many thousands of sent WhatsApp photos takes a little longer to sync.
- Photos copied by a move are new files, so other gallery apps may list them as recently added.
