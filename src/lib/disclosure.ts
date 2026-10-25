/**
 * Prominent disclosure shown in-app before sending the user to Accessibility settings.
 * Google Play requires this for non-accessibility-tool use of the AccessibilityService API:
 * https://support.google.com/googleplay/android-developer/answer/10964491
 * Text mirrored in docs/PLAY_POLICY.md — keep both in sync.
 */
export const ACCESSIBILITY_DISCLOSURE = [
  'LOCKIN uses the Accessibility Service API to detect which app comes to the front while a focus session you started is running.',
  'What it reads: only the package name of the app in front (for example "com.instagram.android"). It does not read text, passwords, messages or anything else on screen.',
  'What it does: if that app is not on your allowlist, LOCKIN shows its own full-screen reminder on top. It never taps, types, swipes or presses buttons for you.',
  'Where data goes: nowhere. LOCKIN has no internet permission. The package names of blocked apps are kept only on this phone, in the LOCKIN block log.',
  'This is optional. Without it LOCKIN still works using Usage access, but it may react a little slower.',
];
