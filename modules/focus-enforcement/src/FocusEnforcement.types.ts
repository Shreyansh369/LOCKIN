export type Variant = 'play' | 'hardline';

export type PermissionKind =
  | 'usageAccess'
  | 'overlay'
  | 'notifications'
  | 'accessibility'
  | 'appInfo'
  | 'battery'
  | 'deviceAdmin';

export type PermissionStatus = {
  variant: Variant;
  sdkInt: number;
  manufacturer: string;
  installer: string | null;
  usageAccess: boolean;
  overlay: boolean;
  notifications: boolean;
  accessibility: boolean;
  accessibilityConnected: boolean;
  /** Heuristic: API 33+, not installed by Play, accessibility not enabled yet. */
  restrictedSettingsLikely: boolean;
  batteryUnrestricted: boolean;
  deviceAdmin: boolean;
  deviceOwner: boolean;
};

export type LaunchableApp = {
  packageName: string;
  label: string;
  /** file:// URI of a cached 96px PNG, or null. */
  icon: string | null;
  /** Dialer, launcher, IME, Settings… never blocked, no need to allowlist. */
  alwaysAllowed: boolean;
};

/** strict = no breaks. */
export type SessionMode = 'standard' | 'strict';

export type StartSessionOptions = {
  task: string;
  durationMin: number;
  allowlist: string[];
  mode: SessionMode;
};

export type ActiveSessionState = {
  active: true;
  sessionId: string;
  task: string;
  startedAt: number;
  endsAt: number;
  remainingMs: number;
  allowlist: string[];
  mode: SessionMode;
  breaksUsed: number;
  breaksRemaining: number;
  breakUntil: number | null;
  breakPackages: string[];
  emergencyUnlockAt: number | null;
  blockCount: number;
};

export type SessionState = ActiveSessionState | { active: false };

export type SessionEndType = 'expired' | 'user' | 'emergency';

export type NativeEvent =
  | { type: 'block'; ts: number; sessionId?: string; pkg: string; reason: 'app' | 'protected'; source: string }
  | {
      type: 'session_start';
      ts: number;
      sessionId: string;
      task: string;
      startedAt: number;
      endsAt: number;
      mode: SessionMode;
      allowlist: string;
    }
  | { type: 'session_end'; ts: number; sessionId: string; endType: SessionEndType; reason: string | null }
  | { type: 'break_start'; ts: number; sessionId: string; minutes: number; packages: string }
  | { type: 'break_end'; ts: number; sessionId: string }
  | { type: 'emergency_start'; ts: number; sessionId: string; unlockAt: number };

export type FocusEnforcementEvents = {
  onBlocked: (e: { pkg: string; ts: number }) => void;
  onSessionEnded: (e: { type: SessionEndType; ts: number }) => void;
};
