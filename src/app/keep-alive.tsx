import { Focus } from '../lib/focus';
import { isHardline, usePermissionStatus } from '../lib/permissions';
import { Button, H1, P, Row, Screen, Status } from '../ui/components';

// Steps adapted from dontkillmyapp.com (github.com/urbandroid-team/dont-kill-my-app,
// _vendors-content/<vendor>/user.md). Menu names vary by OS version; keep these short and
// keep docs/DEVICE_TEST.md in sync.
const OEM_STEPS: Record<string, { name: string; steps: string[] }> = {
  xiaomi: {
    name: 'Xiaomi / Redmi / POCO (MIUI, HyperOS)',
    steps: [
      'App info → Autostart (or App permissions → Background autostart): ON.',
      'App info → Battery saver → No restrictions.',
      'Recents: long-press or drag LOCKIN down to lock it (padlock icon).',
    ],
  },
  oppo: {
    name: 'OPPO / OnePlus (ColorOS)',
    steps: [
      'App info → Allow auto start-up (or Auto launch): ON.',
      'App info → Battery usage → Allow background activity / Don\'t optimize.',
      'Recents: open the card menu and Lock LOCKIN.',
    ],
  },
  realme: {
    name: 'realme (realme UI)',
    steps: [
      'Settings → Battery → (Power saving settings →) App battery management → LOCKIN: allow auto-launch, foreground and background activity, turn off "Optimize battery use".',
      'Recents: lock LOCKIN.',
    ],
  },
  vivo: {
    name: 'vivo / iQOO (FuntouchOS, OriginOS)',
    steps: [
      'Settings → (More settings →) Applications → Autostart → LOCKIN: ON.',
      'Settings → Battery → High background power consumption (or Background power consumption management) → LOCKIN: allow.',
      'App info → Battery → Unrestricted / Not optimized.',
      'Recents: swipe the LOCKIN card down and tap the lock.',
    ],
  },
  samsung: {
    name: 'Samsung (One UI)',
    steps: [
      'App info → Battery → Unrestricted.',
      'Settings → Battery → Background usage limits → Never sleeping apps → add LOCKIN.',
      'Same screen: make sure LOCKIN is not in Sleeping or Deep sleeping apps.',
    ],
  },
};

function vendorKey(manufacturer: string): string | null {
  const m = manufacturer.toLowerCase();
  if (['xiaomi', 'redmi', 'poco'].some((k) => m.includes(k))) return 'xiaomi';
  if (['oppo', 'oneplus'].some((k) => m.includes(k))) return 'oppo';
  if (m.includes('realme')) return 'realme';
  if (['vivo', 'iqoo'].some((k) => m.includes(k))) return 'vivo';
  if (m.includes('samsung')) return 'samsung';
  return null;
}

export default function KeepAlive() {
  const { status } = usePermissionStatus();
  if (!status) return <Screen><P>…</P></Screen>;
  const key = vendorKey(status.manufacturer);
  const vendor = key ? OEM_STEPS[key] : null;

  return (
    <Screen>
      <H1>Keep LOCKIN alive</H1>
      <P muted>Detected manufacturer: {status.manufacturer}</P>

      <Row>
        <P style={{ fontWeight: '700' }}>
          Battery optimization <Status ok={status.batteryUnrestricted} label={status.batteryUnrestricted ? 'OFF (good)' : 'ON'} />
        </P>
        <P muted>
          {isHardline
            ? 'LOCKIN HL asks Android directly to exempt it.'
            : 'Opens the battery optimization list. Find LOCKIN and choose "Don\'t optimize" / "Unrestricted".'}
        </P>
        {!status.batteryUnrestricted && <Button title="Battery settings" onPress={() => Focus.requestPermission('battery')} />}
      </Row>

      {vendor ? (
        <Row>
          <P style={{ fontWeight: '700' }}>{vendor.name}</P>
          {vendor.steps.map((s, i) => (
            <P key={i}>
              {i + 1}. {s}
            </P>
          ))}
          <P muted>Menu names differ between OS versions. Most of these start from App info.</P>
          <Button title="Open App info" kind="secondary" onPress={() => Focus.requestPermission('appInfo')} />
        </Row>
      ) : (
        <Row>
          <P>No extra steps known for this manufacturer. Stock Android only needs the battery setting above.</P>
        </Row>
      )}
    </Screen>
  );
}
