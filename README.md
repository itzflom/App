# Last Reel

Kicks you out of Instagram and TikTok once you've been in too long, but only **after you finish the video you're on**. Then it blocks both apps for a break (5 minutes by default).

Android only. See [Why not iPhone?](#why-not-iphone).

## How it works

1. Last Reel counts the time you spend in Instagram + TikTok **combined** (default limit: 10 min).
2. When you hit the limit, you get a toast: *"That's your limit. Finish this video, then you're out."* Nothing is cut off mid-video.
3. You're sent to the home screen at your **next swipe**. If you never swipe (e.g. a video just loops), you're sent home after the *finish-the-video window* (default 90 s).
4. For the next 5 minutes, opening either app bounces you straight back to the home screen and shows a countdown.
5. After the break your allowance resets.

Details worth knowing:

- Leaving the app after your limit still starts the break. You can't dodge it by bailing out early.
- Hopping out of the app for a few seconds doesn't reset your clock. Only a full break's worth of time away does.
- Your rules (limit, break, finish-the-video window) are locked while a break is running, so you can't just shorten the break.

## Privacy

- The accessibility service only receives *which app window is in front* and *that something scrolled*. It is configured with `canRetrieveWindowContent="false"`, so it cannot read anything on your screen.
- The app declares **no permissions**: no internet, no storage, nothing.
- All state stays on the device.

## Install

**Get the APK**
- From a build: the **Build APK** GitHub Action uploads `app-debug.apk` as an artifact on every push (Actions tab → latest run → Artifacts).
- Or build it yourself: `./gradlew assembleDebug`, output in `app/build/outputs/apk/debug/`.

**Enable it** (one-time; Android won't let an app turn this on for you)
1. Open Last Reel and tap **Open Accessibility settings**.
2. Pick **Last Reel** under *Installed apps* and switch it on.

> **Switch greyed out?** Android 13+ blocks accessibility for apps installed from a file. Go to *Settings → Apps → Last Reel → ⋮ menu → Allow restricted settings*, then try again.

## Build & test

Requires JDK 17+ and the Android SDK (platform 37).

```sh
./gradlew testDebugUnitTest   # engine unit tests
./gradlew assembleDebug       # debug APK
./gradlew lintDebug
```

## Code map

| Path | What it is |
|---|---|
| `engine/LimitEngine.kt` | Pure-Kotlin state machine: all the "when do I kick you" rules. Unit-tested. |
| `service/GuardService.kt` | Accessibility service: turns system events into engine events and performs kicks. |
| `data/GuardRepository.kt` | Observable, persisted state shared by the service and the UI. |
| `ui/`, `MainActivity.kt`, `LockActivity.kt` | Jetpack Compose screens (home + break screen). |

## Known limits (please read)

- **"Finish the video" is a heuristic.** Instagram and TikTok don't tell other apps when a video ends, and Last Reel deliberately can't read the screen. It treats your *next scroll/swipe* as "you've moved on", with the finish-the-video window as a backstop for looping videos. This was verified in unit tests against a fake clock, **not against the real Instagram/TikTok apps**. If a particular app version doesn't emit scroll events, the backstop timeout is what kicks you; tune it in the app.
- **The break screen isn't guaranteed.** The kick itself is Android's "Home" action and always works. The countdown screen and toast are layered on top: Android doesn't promise an accessibility service may open an activity from the background, so on some devices you may only see the home screen.
- **It's your phone.** You can switch the accessibility service off in Settings, or uninstall the app. Last Reel doesn't try to stop you.
- Uses the wall clock for the break, so changing the system time would shorten it.

## Why not iPhone?

iOS doesn't let one app observe or close another app. The only related tool is Apple's Screen Time API, which can lock an app after a time limit but has no way to wait for a video to finish, and needs a special Apple entitlement. So this can't be built on iOS the way you described.
