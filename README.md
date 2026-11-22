# ScreenLock

Android app that locks the touchscreen, so a person's touches can't interfere while something else drives the phone (an agent over adb, or [Phone Relay](https://github.com/ThomasWCode/Phone-Relay)). Input sent over adb or by other accessibility services keeps working while it is locked. Built for sideloading (target device: Samsung Galaxy S24; minimum Android 14).

## How it works

- An accessibility service (`LockService`) asks Android for every event from the touchscreen (`AccessibilityServiceInfo.setMotionEventSources(SOURCE_TOUCHSCREEN)`). Android then hands those events to the service instead of to the apps, so a finger on the glass reaches nothing: no app, no navigation gestures, no notification shade.
- Injected input does not come from the touchscreen and joins the input pipeline after that point, so it is never taken: adb's `input tap/swipe/keyevent/text`, and gestures that accessibility services dispatch (`dispatchGesture`, which Phone Relay taps with).
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

- An injected tap on the widget (adb or Phone Relay) also toggles it, since only a finger is blocked.
- A reboot always comes back unlocked.

## Safety

- If the service crashes or is turned off, Android stops giving it the touchscreen, so the phone is unlocked (it fails open, never stuck).
- `uiautomator dump` (and Phone Relay's `/ui?engine=uiautomator`) unbinds every accessibility service for a moment, so the lock drops for that moment. The service puts it back by itself when it reconnects within 60 seconds in the same boot.
- The phone's own lock screen is behind the same lock: hold two fingers to unlock ScreenLock first, then unlock the phone as usual.
- A Phone Relay request card (`POST /request`) can't be answered by a finger while locked.

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
