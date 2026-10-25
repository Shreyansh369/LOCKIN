import { NativeModule, requireNativeModule } from 'expo';

import type {
  FocusEnforcementEvents,
  LaunchableApp,
  NativeEvent,
  PermissionKind,
  PermissionStatus,
  SessionState,
  StartSessionOptions,
  Variant,
  ActiveSessionState,
} from './src/FocusEnforcement.types';

export * from './src/FocusEnforcement.types';

declare class FocusEnforcementModule extends NativeModule<FocusEnforcementEvents> {
  getVariant(): Variant;
  getPermissionStatus(): Promise<PermissionStatus>;
  /** Opens the Settings screen for the permission. Resolves false if unsupported for this variant. */
  requestPermission(kind: PermissionKind): Promise<boolean>;
  listLaunchableApps(): Promise<LaunchableApp[]>;
  startSession(options: StartSessionOptions): Promise<ActiveSessionState>;
  /** Max 2 per session, 1–15 min. Empty `packages` = every app. */
  requestBreak(minutes: number, packages: string[]): Promise<void>;
  endSession(reason: string): Promise<void>;
  /** Starts the non-skippable 15-minute countdown. Resolves the unlock timestamp. */
  startEmergencyUnlock(): Promise<number>;
  getSessionState(): Promise<SessionState>;
  /** Returns and clears the native event queue. */
  drainEvents(): Promise<NativeEvent[]>;
  setProfanityEnabled(enabled: boolean): Promise<void>;
  getProfanityEnabled(): Promise<boolean>;
}

export default requireNativeModule<FocusEnforcementModule>('FocusEnforcement');
