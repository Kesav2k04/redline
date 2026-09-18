# Redline

Reads a rental lease and points at the clauses that will cost you money.

Everything runs on the device. No account, no upload, no network call. You paste the
lease or share it from whatever app it arrived in, and the text never leaves the phone.

## Status

Early. The input path and the clause splitter work and are covered by tests. The
layer that decides which clauses are costly is not written yet.

## Build

Needs the Android SDK with platform 36 and a JDK 17 or newer.

```
git clone https://github.com/Kesav2k04/redline
cd redline
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

```
./gradlew :app:testDebugUnitTest
```

No API keys, no `local.properties` entries beyond `sdk.dir`, and nothing to sign up for.

## How the text gets in

Three ways, all of which end in the same place:

- Paste it into the box.
- Share it to Redline from any app (`ACTION_SEND`, `text/plain`).
- Select text anywhere in Android and pick Redline from the selection menu
  (`ACTION_PROCESS_TEXT`).

## Splitting a lease into clauses

Harder than it sounds, which is why it has its own tests. Lease text arrives hard
wrapped at some arbitrary column, with words broken across lines by a hyphen and with
the real structure carried by numbering rather than by blank lines.

`ClauseSplitter` rejoins wrapped lines, repairs hyphenated breaks, treats a new
numbered item as a new clause even with no blank line before it, and only then falls
back to sentence boundaries for blocks that are still too long to be one clause.
Fragments under twenty characters are dropped, because headings are not clauses.

## Licence

MIT. See `LICENSE`.
