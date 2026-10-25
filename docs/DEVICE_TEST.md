# Device test checklist (Phase 0)

Run on a real phone. Emulators don't reproduce OEM process killers. Use a **preview** build for everything except the
timing check, which also runs fine on a dev build.

Fill in for each device: model, Android version / API level, OEM skin + version, variant, build profile.

Handy commands (USB debugging on):

```sh
adb logcat -s LockinEnforcer LockinService LockinDeviceOwner ActivityTaskManager
adb shell dumpsys activity services expo.modules.focusenforcement.EnforcementService
adb shell dumpsys usagestats | head          # usage access sanity check
```

`ActivityTaskManager` prints `Background activity launch blocked!` if Android drops a blocker launch.

## 1. Interception speed

- [ ] Start a 25-min session with only one app allowed. Open a blocked app from the launcher.
      Block screen appears **< 300 ms** after the app's first frame (record the screen at 60 fps and count frames).
  - [ ] with accessibility ON
  - [ ] play only: with accessibility OFF (UsageEvents polling only; expect up to ~500 ms + launch time)
- [ ] Open the blocked app from Recents, from a notification, and from a share sheet. Each one gets covered.
- [ ] Back gesture / back button on the block screen goes to the launcher, never into the blocked app.
- [ ] "Back to work" goes to the launcher. "Exit ritual" opens LOCKIN on `/exit`.
- [ ] Block counter on `/focus/session` goes up by one per attempt.

## 2. Survival

- [ ] **Screen off 30 min** mid-session, then unlock and open a blocked app: still covered. Notification still shows
      time left.
- [ ] **Swipe LOCKIN away from Recents** mid-session: notification stays, blocked apps still covered.
- [ ] **Reboot mid-session**: after unlocking, the notification comes back by itself (BootReceiver) and blocked apps
      are covered. Without opening LOCKIN.
- [ ] **Force stop by the system**: `adb shell am kill <package>` (simulates a low-memory kill). The service comes back
      (START_STICKY) within a minute, or at the latest when accessibility reconnects or LOCKIN is opened.
- [ ] Session expires while the screen is off: next unlock shows no notification, nothing blocked, and the session row
      in SQLite has `end_type = 'expired'` after LOCKIN is opened.

## 3. Always reachable

- [ ] Default dialer opens and places a call during a session (not allowlisted).
- [ ] Emergency dialer from the lock screen works during a session.
- [ ] Incoming call UI is not covered.
- [ ] Keyboard works in allowlisted apps. Launcher and notification shade are never covered.
- [ ] play: Android Settings opens normally during a session.

## 4. Exit paths

- [ ] Exit ritual: text box stays disabled for 45 s; < 15 words is refused; reusing an earlier reason (Jaccard > 0.6) is
      refused and the matching old reason is shown; the last 5 reasons are listed.
- [ ] Break: unlocks only the selected apps (or all, if none selected) for N minutes; relocks on time; a third break is
      refused; strict mode offers no break.
- [ ] **Emergency unlock**: 15:00 countdown shows on `/focus/session` and in the notification; blocking stays on
      during the countdown; dialer works during it; at 0 the session ends.
- [ ] After emergency unlock (hardline + Device Owner): no app is suspended (`adb shell pm list packages -u` +
      try launching a few), `adb shell dumpsys device_policy | grep -i -A2 safe_boot` shows no restriction, LOCKIN HL
      uninstall is no longer blocked.
- [ ] Same full revert after normal expiry and after "End session".

## 5. Restricted settings (fresh sideload, Android 13+)

- [ ] Uninstall, then install the preview APK from the EAS link with the browser.
- [ ] Try enabling LOCKIN's accessibility service: Android shows "Restricted setting".
- [ ] LOCKIN's permissions screen shows the restricted-settings card. "Open App info" → ⋮ → **Allow restricted
      settings** → PIN → back to LOCKIN → status re-checks on resume → enabling now works.
- [ ] Repeat on Android 15+ (Enhanced Confirmation Mode). Note any extra screens; update the in-app steps if they
      differ.

## 6. Hardline Settings guard (hardline only)

During a session, each of these is covered within a second, and Home is pressed first:

- [ ] App info for LOCKIN HL (Settings → Apps → LOCKIN HL), and with it Force stop and Uninstall
- [ ] Long-press launcher icon → App info
- [ ] Accessibility settings (main page and the LOCKIN entry)
- [ ] Device admin apps (Settings → Security → Device admin apps, path varies)
- [ ] Usage access / Display over other apps pages for LOCKIN HL
- [ ] Uninstall dialog (drag icon to "Uninstall")
- [ ] **Not** covered: Wi-Fi, Bluetooth, Display, Sound, Battery main page

Detection heuristics, all in `SettingsGuard.kt`, and how they break:

| Heuristic | Breaks when |
| --- | --- |
| Settings host package resolved from `ACTION_SETTINGS`, `ACTION_APPLICATION_DETAILS_SETTINGS`, `ACTION_ACCESSIBILITY_SETTINGS` | OEM hosts a page in a different package that isn't the resolved handler (e.g. a security-center app only for some pages) |
| Window class contains `InstalledAppDetails` / `AppInfo` / `Accessibility` / `DeviceAdmin` / `Uninstall` | OEM routes every page through a generic `SubSettings`. Common on Samsung, Xiaomi |
| Window title contains `accessibility`, `device admin`, `admin apps`, `force stop`, `uninstall` | Non-English locale; OEM wording |
| Our label `LOCKIN HL` appears anywhere in the active window | Label truncated or rendered as an image. Also over-blocks: the Apps list and per-app lists that show every app get covered during sessions |

Record which heuristic fired (`adb logcat`; the block event has `reason=protected`) and add OEM-specific class names
to `CLASS_MARKERS` as you find them.

- [ ] Device admin active: uninstall from the launcher is refused until admin is deactivated.

## 7. Play variant restraint

- [ ] With accessibility ON, `adb shell dumpsys accessibility` lists LOCKIN's service; check that its event types are
      window-state changes only and it cannot retrieve window content (output format varies by Android version).
- [ ] During a session, nothing presses Home/Back by itself: opening a blocked app shows the block screen *over* it;
      the app underneath is only left when the user taps a button. (`grep -rn performGlobalAction` on the source:
      only `SettingsGuard.kt`, which is hardline-gated.)
- [ ] Settings → Accessibility → LOCKIN shows the play description text.

## 8. Release artifacts

- [ ] `scripts/verify-release.sh lockin-preview.apk` → `RESULT: PASS`
- [ ] `scripts/verify-release.sh lockin-hl-preview.apk` → `RESULT: PASS`
- [ ] Both installed side by side; two icons (white and red padlock), two notifications if both run.

## 9. OEM battery / autostart

LOCKIN → Settings → Keep LOCKIN alive shows the steps for the detected manufacturer. Steps adapted from
[dontkillmyapp.com](https://dontkillmyapp.com) (source: `github.com/urbandroid-team/dont-kill-my-app`,
`_vendors-content/<vendor>/user.md`). Menu names change between OS versions; record the real path on your device.

**All phones**
- App info → Battery → Unrestricted / Don't optimize. (hardline asks directly; play opens the list.)

**Xiaomi / Redmi / POCO (MIUI, HyperOS)**
- App info → Autostart ON (MIUI 14+: App permissions → Background autostart).
- App info → Battery saver → No restrictions.
- Security app → Permissions → Auto-start (older MIUI).
- Lock LOCKIN in Recents (drag down / padlock).

**OPPO / OnePlus (ColorOS), realme (realme UI)**
- App info → Allow auto start-up (Auto launch).
- App info → Battery usage → allow background activity (realme: Settings → Battery → (Power saving settings →) App
  battery management → LOCKIN: allow auto-launch, foreground and background activity, optimize off).
- Lock in Recents.
- Known issue (dontkillmyapp): some Oppo builds kill accessibility services on screen-off; they then need re-enabling.
  Test item 2 catches this.

**vivo / iQOO (FuntouchOS, OriginOS)**
- Settings → (More settings →) Applications → Autostart → LOCKIN ON.
- Settings → Battery → High background power consumption (or Background power consumption management) → allow.
- App info → Battery → not optimized.
- Lock in Recents.

**Samsung (One UI)**
- App info → Battery → Unrestricted.
- Settings → Battery → Background usage limits → Never sleeping apps → add LOCKIN; make sure it's not in Sleeping /
  Deep sleeping apps. ("Put unused apps to sleep" is the usual culprit.)

- [ ] After the steps, rerun section 2 on that phone.
