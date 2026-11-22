# ScreenLock

Android app that locks the touchscreen, so a person's touches can't interfere while something else drives the phone (an agent over adb, or [Phone Relay](https://github.com/ThomasWCode/Phone-Relay)). Input sent over adb or by other accessibility services keeps working while it is locked. Built for sideloading (target device: Samsung Galaxy S24; minimum Android 14).

## How it works

- An accessibility service (`LockService`) asks Android for every event from the touchscreen (`AccessibilityServiceInfo.setMotionEventSources(SOURCE_TOUCHSCREEN)`). Android then hands those events to the service instead of to the apps, so a finger on the glass reaches nothing: no app, no navigation gestures, no notification shade.
- Input injected over adb (`input tap/swipe/keyevent/text`) joins the input pipeline after that point, so it is never taken.
- Gestures that accessibility services dispatch (`dispatchGesture`, which Phone Relay taps and swipes with) are different: Android's gesture injector sits *before* the interception, so they are taken like a finger's. Phone Relay therefore pauses the lock around each of its gestures (below). Other services' gestures are blocked while locked.
- Phone Relay's typing, keys, screenshots and screen reading don't go through the touchscreen, so the lock never affects them.
- Nothing is drawn on the screen, so screenshots look exactly as they do without the lock.
- Unlocked, the service takes in nothing and does nothing.
- The volume and power buttons are not blocked.

## Lock and unlock

- **Lock:** tap the **Touch lock** widget (1x1, grey "Off", indigo "On"). The phone buzzes once.
- **Unlock:** hold two fingers still on the screen for 3 seconds; the phone buzzes twice. Lift them and the lock turns off (one longer buzz). A third finger, sliding more than about 7 mm, or lifting one finger early starts it over. The gesture itself never reaches the app underneath. Touching the screen while locked shows a short reminder of this, at most every 10 seconds.
- **From the shell** (adb, or Phone Relay's `/intent` with `as_shell`): the receiver only accepts senders holding `DUMP`, which the shell has and apps don't. The reply's data is the state afterwards (`locked`, `unlocked`, or `service off`).

  ```bash
  adb shell am broadcast -a com.thomaswcode.screenlock.LOCK   -n com.thomaswcode.screenlock/.CommandReceiver
  adb shell am broadcast -a com.thomaswcode.screenlock.UNLOCK -n com.thomaswcode.screenlock/.CommandReceiver
  adb shell am broadcast -a com.thomaswcode.screenlock.STATUS -n com.thomaswcode.screenlock/.CommandReceiver
  ```

- An injected tap on the widget (adb, or Phone Relay with its pause) also toggles it, since only a finger is blocked.
- A reboot always comes back unlocked.

## Phone Relay while locked

- **One finger in a straight line** (a tap, a swipe, a long press): Phone Relay sends it through its shell bridge (`input swipe`) when the bridge answers. Shell input passes the lock without pausing it, so a finger on the screen (in a pocket, say) neither gets through nor cancels the gesture. The reply's `via` is `shell`.
- **Anything else**, or with the shell bridge unreachable (on mobile data unless set up for it): an accessibility gesture, with the lock paused for its own time (`via` is `gesture`).
- `PauseProvider` (`content://com.thomaswcode.screenlock.pause`) has three calls: `state`, `pause` (with `ms`, the longest it may last; at most 65 s, 2 s if not given) and `resume`. During a pause the touchscreen goes back to the apps; it ends at `resume` or when `ms` runs out, whichever comes first. Each reply's `locked` says whether the lock is on.
- Only Phone Relay (checked by its signing certificate) and the shell may call it:

  ```bash
  adb shell content call --uri content://com.thomaswcode.screenlock.pause --method pause --extra ms:l:2000
  adb shell content call --uri content://com.thomaswcode.screenlock.pause --method resume
  adb shell content call --uri content://com.thomaswcode.screenlock.pause --method state
  ```

- With a pause, a new touch can get through only during that Relay gesture (50 ms for a tap, a swipe's own length). Android's gesture injector can also cancel a paused gesture when a real touch arrives, though that did not happen on the test phone (Android 17) with a finger rubbing or tapping during a Relay swipe.

## Safety

- If the service crashes or is turned off, Android stops giving it the touchscreen, so the phone is unlocked (it fails open, never stuck).
- `uiautomator dump` (and Phone Relay's `/ui?engine=uiautomator`) unbinds every accessibility service for a moment, so the lock drops for that moment. The service puts it back by itself when it reconnects within 60 seconds in the same boot.
- The phone's own lock screen is behind the same lock: hold two fingers to unlock ScreenLock first, then unlock the phone as usual.
- A Phone Relay request card (`POST /request`) can't be answered by a finger while locked.
- The lock refuses to turn on while explore by touch (TalkBack or similar) is on, and lets go if that comes on while locked: Android stops sending the touchscreen to the service then, so the lock would do nothing.

## Install (sideload)

1. Build it (below), then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
2. Settings > Accessibility > Installed apps > **ScreenLock**: turn it on. On a sideloaded app Android may first ask you to allow restricted settings (Settings > Apps > ScreenLock > ⋮ > Allow restricted settings).
3. Long-press an empty spot on the home screen, **Widgets**, **ScreenLock**, and drag the **Touch lock** widget (1x1) onto the home screen. It reads "Set up" until the service is on.

## Build

Requirements: JDK 17 and the Android SDK (platform 36).

```bash
# Install the Android SDK command-line tools, then:
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
# Unit tests, the APK and lint (what CI runs):
./gradlew testDebugUnitTest assembleDebug lintDebug
# Only the unit tests:
./gradlew testDebugUnitTest
```

The debug build is signed with `app/debug.keystore` when that file exists. It is kept out of the repository, because a published key would let anyone build an update that installs over the app. Without it (CI, a fresh clone) Android's default debug key signs it.
