# PLVinyl

Whatever you're playing in Spotify, YouTube Music or any other player, shown as a record
on a turntable. The needle drives the music: lift the arm and the song pauses, drop it
anywhere on the record and it plays from there.

No ads, no account, no internet permission. Everything stays on your phone.

## What it does

- **A semi-automatic turntable.** The arm never slides across the record: it lifts,
  swings, then lowers, like the real thing. At the end of a song the needle runs into
  the lead-out, circles the locked groove, and the next record is swapped on.
- **Front panel keys.** ▶■ start/stop, ▲▼ cue (pause in place), ↻1 repeat one, and
  |◀◀ ▶▶| to flick through songs. Hold them to run through several; only the song you
  land on gets a record swap.
- **Grab the arm** and drop it anywhere to cue that point in the song, or **swipe the
  record** to change songs.
- **7" singles and 12" LPs**, picked from the song's length. Six backgrounds, two of
  them real oiled wood (walnut and oak), or your own paint; four vinyl finishes
  (including marbled vinyl coloured from the album art); and record wear that builds up
  the more you play something.
- **Paint it.** Give the deck and the front panel your own colors: hand-picked hi-fi
  paints in one tap, or any color with hue / saturation / lightness sliders, previewed
  live on the turntable itself.
- **Light that follows the sun**, wherever your phone's time zone says you are (no
  location used): a soft glow at sunrise, light swinging from right to left through the
  day, golden hour as the sun sets, then a desk lamp. The record's shine and the shadows
  move with it. Or hold it at your favourite time of day.
- **Liner notes:** swipe left for your listening stats (most played, top artist, when
  you listen, streaks), counted on the phone only.
- Optional **surface noise**, synthesized on the device.

## How it's built

Two Gradle modules:

```
:app   Android: media sessions, storage, Compose UI, animation
  │
  ▼
:core  Plain Kotlin/JVM. Every rule lives here. No Android imports.
```

```
 player reports ──▶ ┌──────────────────────┐ ──▶ player commands
 (PlayerSnapshot)   │  TurntableController │     (play, pause, seek, skip)
 your taps ───────▶ │                      │ ──▶ TurntableUiState (the screen)
 (UserIntent)       └──────────┬───────────┘
                               │ TurntableTarget
                               ▼
                         MovePlanner ──▶ one physical move ──▶ TurntableAnimator
                               ▲                                   │
                               └───────── move finished ◀──────────┘
```

- **`:core`** decides. `PlaybackArbiter` (music plays only while the needle is down),
  `MovePlanner` (the turntable's physical rules), `EndOfRecord`, `SkipQueue`,
  `CrossfadeLearner`, `SessionChooser`, `PlayCounter`, `StatsCalculator` and friends
  are plain classes with no timers inside. Time comes in through a `Clock`, so tests
  can jump ahead instantly.
- **`:app`** talks to Android and draws. `MediaSessionWatcher` turns media sessions
  into snapshots; `TurntableAnimator` animates each planned move; the drawing code
  bakes static layers into bitmaps so a spinning record costs one GPU rotation per frame.

## Easy on the battery

Redrawing the screen is what uses the battery (and warms the phone), so the app only
redraws what has visibly changed:

- Everything that moves keeps one beat: the record redraws 30 times a second at slow
  speeds (60 at 33⅓), and a scrolling title moves on the same frames.
- The tonearm is redrawn only when it has moved a visible amount: while a song plays,
  the needle creeps well under a pixel a second.
- When the phone says it's warm, or Battery Saver is on, motion gets lighter; when it's
  hot, the record turns in slow steps until it cools down.
- The platter stops half a minute after the music does, and after 5 minutes without a
  touch the screen dims.

`tools/battery_check.sh` measures frames per second, the app's CPU use and the phone's
temperatures over a minute, to compare before and after a change.

## Tests

```
./gradlew :core:test
```

Over 170 unit tests run on the JVM in a few seconds, with no device or emulator needed.
Many of them are regression tests named after a bug the first version had, for example
*"a seek lifts the needle first and never swings it across the record"* and *"a
notification sound or a paused Chrome tab doesn't steal the session"*.

## Building

Android Studio (AGP 9), JDK 17+, min SDK 24.

```
./gradlew :app:installRelease
```

Release builds have R8 on and are signed with the debug key, so they install on your
own phone. On first launch the app asks for notification access: that's how Android
shares what's playing. If the switch is greyed out (Android does that for apps
installed outside the Play Store), open App info → ⋮ → *Allow restricted settings*.

YouTube (the video app) only shares what's playing with Premium; YouTube Music works.

## Credits

- Walnut and oak: CC0 veneer scans from [Poly Haven](https://polyhaven.com)
  (*Black Walnut Veneer 02* and *Red Oak Veneer*), cropped and given an oiled finish by
  [`tools/make_wood.py`](tools/make_wood.py).
- Where the sun is: the reference cities in the tz database's `zone.tab` (public domain).
