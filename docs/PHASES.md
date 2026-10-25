# Phases (outline)

Phase 0 is done when `docs/DEVICE_TEST.md` passes on at least one stock-ish phone and one aggressive OEM phone.
Everything below is an outline, not a commitment.

## Phase 1 — default-on windows and the unlock surface

- Scheduled focus windows (e.g. weekdays 09:00–12:00) that start a session automatically with a stored task +
  allowlist. Native scheduling (exact alarm or WorkManager), so JS isn't needed to start.
- Unlock commitment card: on `ACTION_USER_PRESENT`, show a native card ("You're in a window: <task>. 1h12m left.")
  before the launcher. Decide: overlay vs. activity, and how it interacts with the lock screen.
- Reminders limited to two surfaces only: the unlock card and the blocker screen. No push-style notifications.
- Open: does a scheduled start count as "user-started" for the play specialUse justification?

## Phase 2 — stats

- Unlocks per session (needs `ACTION_USER_PRESENT` counting during sessions).
- Block hits per session and per app (already in `block_events`).
- Exits by type: break / end / emergency / expired (`exit_events`, `sessions.end_type`).
- Block → next-unlock recovery time: time from a block event to the next screen unlock, as a "how fast did I give up
  trying" signal.
- One screen, local only, no charts library unless a simple bar list stops being enough.

## Phase 3 — optional face-down start

- Start (or confirm) a session by placing the phone face-down: accelerometer z ≈ −g plus proximity "near".
- Sample sensors only while a session is pending or active; nothing in the background otherwise.
- Opt-in, off by default. Measure battery cost on the test devices before keeping it.

## Phase 4 — store prep (play variant only)

- Replace `com.CHANGEME`, final icons, store listing, privacy policy (states: no data collected).
- Play Console: AccessibilityService declaration + video, foreground service (specialUse) declaration, data safety
  form, target API check. Drafts in `docs/PLAY_POLICY.md`.
- Re-audit that no hardline-only component is in the AAB (`verify-release.sh` + manifest grep).
- Decide whether play ships with accessibility at all.
- `eas build --profile production`; Play App Signing with the EAS keystore as upload key.
