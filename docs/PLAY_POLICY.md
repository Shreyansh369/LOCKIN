# Google Play policy notes (play variant only)

Draft for a future store submission. Nothing here has been submitted. Re-check every policy link before submitting;
they change.

Sources:
- AccessibilityService API policy: https://support.google.com/googleplay/android-developer/answer/10964491
- Foreground service types (specialUse): https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- Target API requirement: https://developer.android.com/google/play/requirements/target-sdk
  (new apps/updates must target API 36 from 2026-08-31; Expo SDK 58 defaults to targetSdk 36)
- Package visibility: https://developer.android.com/training/package-visibility/declaring
- Doze exemptions: https://developer.android.com/training/monitoring-device-state/doze-standby#support_for_other_use_cases

## 1. Accessibility declaration (Play Console → App content → Accessibility API) — draft answers

**Is your app an accessibility tool (isAccessibilityTool)?** No. The service does not set `android:isAccessibilityTool`.
Play's policy lists "automation tools" and "monitoring apps" as not accessibility tools; LOCKIN doesn't claim to be one.

**What does the app use the AccessibilityService API for?**
> LOCKIN is a focus app. While a focus session the user started is running, the accessibility service receives
> window-state-change events and reads only the package name of the app that came to the foreground. If that app is
> not on the user's allowlist, LOCKIN opens its own full-screen reminder screen. The service is optional; LOCKIN also
> works with Usage access, more slowly.

**What data is accessed / collected?**
> Only the foreground app's package name, during user-started sessions. No screen text, no input, no window content
> (`canRetrieveWindowContent="false"`). Blocked package names and timestamps are stored locally in the app's SQLite
> database. The app has no INTERNET permission; nothing is transmitted or shared.

**Does the service perform actions on the user's behalf?**
> No. It never calls `performGlobalAction`, never dispatches gestures, never clicks, types or changes settings.
> It only starts LOCKIN's own activity. All decisions are deterministic and set by the user beforehand (their
> allowlist and session length); there is no autonomous planning or decision-making.

**Video / walkthrough for review:** show the prominent disclosure, consent, enabling the service, a session, and a
blocked app being covered by the reminder.

### Accessibility config shipped in play

`modules/focus-enforcement/android/src/main/res/xml/lockin_accessibility_service.xml`:
`typeWindowStateChanged`, `feedbackGeneric`, `flagDefault`, `canRetrieveWindowContent="false"`, no
`canPerformGestures`, no `canRequestFilterKeyEvents`, no `isAccessibilityTool`.

## 2. Prominent disclosure (shown in-app before opening Accessibility settings)

Source of truth: `src/lib/disclosure.ts`. Keep identical.

> **How LOCKIN uses Accessibility**
>
> LOCKIN uses the Accessibility Service API to detect which app comes to the front while a focus session you started is
> running.
>
> What it reads: only the package name of the app in front (for example "com.instagram.android"). It does not read
> text, passwords, messages or anything else on screen.
>
> What it does: if that app is not on your allowlist, LOCKIN shows its own full-screen reminder on top. It never taps,
> types, swipes or presses buttons for you.
>
> Where data goes: nowhere. LOCKIN has no internet permission. The package names of blocked apps are kept only on this
> phone, in the LOCKIN block log.
>
> This is optional. Without it LOCKIN still works using Usage access, but it may react a little slower.
>
> [I agree, open Accessibility settings]  [No thanks]

The disclosure is a separate full-screen modal, shown on every tap of "Turn on accessibility" until the service is
on, and requires an affirmative tap before Settings opens. "No thanks" leaves the app fully usable.

## 3. Foreground service — specialUse justification

Manifest: `android:foregroundServiceType="specialUse"` with
`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` =
> User-started focus session: watches which app is in the foreground and covers apps outside the user's allowlist
> until the session ends. Runs only while a session is active and shows the task and time left.

Play Console declaration (App content → Foreground service types), draft:
> The service runs only between the user tapping "Lock in" and the end of the session they chose (or their exit). It
> polls the user's foreground app (UsageStatsManager) every 500 ms while the screen is on, and covers apps outside
> the allowlist. It must keep running while the user is in other apps, which is the entire point of the feature, and no
> other foreground service type describes "enforce a user-set app restriction for a bounded period". The persistent
> notification shows the task and remaining time. Stopping: session end, emergency unlock, or user exit.

Why not other types: not media, location, data sync, health, connected device, phone call, or remote messaging;
`shortService` (~3 min) is too short; `dataSync` has a 6 h/24 h cap and isn't the use case.

## 4. Permissions (play variant)

| Permission | Why | Notes |
| --- | --- | --- |
| `FOREGROUND_SERVICE` | Session service | |
| `FOREGROUND_SERVICE_SPECIAL_USE` | specialUse type | Declared in Play Console |
| `PACKAGE_USAGE_STATS` | Detect foreground app (UsageEvents) | Special app-op, user grants in Settings → Usage access |
| `SYSTEM_ALERT_WINDOW` | Background-activity-launch exemption so the block screen can open over the blocked app | User grants in Settings; no overlay window is drawn |
| `POST_NOTIFICATIONS` | Session notification (task + time left) | Runtime, optional |
| `RECEIVE_BOOT_COMPLETED` | Resume an unfinished session after reboot | |
| `BIND_ACCESSIBILITY_SERVICE` (service permission) | Optional faster detector | See §1–2 |
| `<queries>` MAIN/LAUNCHER, MAIN/HOME, SETTINGS, APPLICATION_DETAILS_SETTINGS, ACCESSIBILITY_SETTINGS, DELETE, DIAL | List launchable apps for the allowlist; resolve launcher, dialer and Settings hosts that are never blocked | Instead of `QUERY_ALL_PACKAGES` |

Explicitly **not** requested in play: `INTERNET`, `ACCESS_NETWORK_STATE` (blocked in release), `QUERY_ALL_PACKAGES`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (play only opens the battery list), `BIND_DEVICE_ADMIN` receiver,
storage, `VIBRATE`.

Data safety form: no data collected, no data shared (nothing leaves the device). `allowBackup=false` and
data-extraction rules exclude everything.

## 5. Must never ship in the play variant

These exist in the shared codebase but are gated to hardline; `scripts/verify-release.sh` output and a manifest review
must confirm they're absent from any play build:

- `LockinDeviceAdminReceiver` declaration and `res/xml/lockin_device_admin.xml` (plugin injects them only for
  `variant: 'hardline'`).
- Device Owner calls: `setPackagesSuspended`, `setUninstallBlocked`, `DISALLOW_SAFE_BOOT`, `clearDeviceOwnerApp`
  (`DeviceOwnerControls` no-ops unless `Variant.isHardline` and Device Owner).
- `SettingsGuard`: reading window titles/content and `performGlobalAction(GLOBAL_ACTION_HOME)`; covering Settings
  screens. Play must leave Settings fully usable.
- Hardline accessibility config (`canRetrieveWindowContent="true"`, written by the plugin only for hardline).
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and the direct exemption intent.
- Any "anti-uninstall" or "anti-disable" behaviour.

Before a store build: grep the merged manifest of the AAB (`bundletool dump manifest`) for `device_admin`,
`BIND_DEVICE_ADMIN`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and confirm `lockin.variant=play`.

## 6. Open questions before submitting

- Play may still consider "block other apps" via accessibility as acceptable only with the usage-access path as
  default. Consider shipping play with accessibility off by default (it already is optional).
- `SYSTEM_ALERT_WINDOW` is requested for its background-launch exemption only. Explain this in the review notes; an
  alternative is to rely on accessibility for launches and make overlay optional.
- Signing: EAS-managed keystore for `com.CHANGEME.lockin` becomes the **upload key** under Play App Signing.
