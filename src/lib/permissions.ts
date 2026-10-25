import { useCallback, useEffect, useState } from 'react';
import { AppState, PermissionsAndroid } from 'react-native';

import { Focus, PermissionStatus } from './focus';

export const variant = Focus.getVariant();
export const isHardline = variant === 'hardline';

/**
 * Session start is gated on these.
 * play: UsageEvents polling is the primary detector -> usage access.
 * hardline: accessibility is the primary detector.
 * Both: overlay ("Display over other apps") is the background-activity-launch exemption the
 * blocker screen relies on.
 */
export function missingRequired(s: PermissionStatus): string[] {
  const missing: string[] = [];
  if (isHardline ? !s.accessibility : !s.usageAccess) missing.push(isHardline ? 'accessibility' : 'usageAccess');
  if (!s.overlay) missing.push('overlay');
  return missing;
}

/** Live permission status; refreshes on mount, on app resume, and on demand. */
export function usePermissionStatus() {
  const [status, setStatus] = useState<PermissionStatus | null>(null);
  const refresh = useCallback(async () => setStatus(await Focus.getPermissionStatus()), []);
  useEffect(() => {
    refresh();
    const sub = AppState.addEventListener('change', (s) => s === 'active' && refresh());
    return () => sub.remove();
  }, [refresh]);
  return { status, refresh };
}

/** POST_NOTIFICATIONS is a runtime permission on API 33+; below that it's a Settings toggle. */
export async function requestNotifications(sdkInt: number): Promise<void> {
  if (sdkInt >= 33) {
    const res = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
    if (res === PermissionsAndroid.RESULTS.GRANTED) return;
  }
  await Focus.requestPermission('notifications');
}
