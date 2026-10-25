import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { Switch, View } from 'react-native';

import { Focus } from '../lib/focus';
import { isHardline, usePermissionStatus } from '../lib/permissions';
import { Button, P, Row, Screen, Status } from '../ui/components';
import { colors } from '../ui/theme';

export default function Settings() {
  const { status } = usePermissionStatus();
  const [profanity, setProfanity] = useState(false);

  useEffect(() => {
    Focus.getProfanityEnabled().then(setProfanity);
  }, []);

  const toggleProfanity = async (v: boolean) => {
    setProfanity(v);
    await Focus.setProfanityEnabled(v);
  };

  return (
    <Screen>
      <Row>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <P style={{ fontWeight: '700' }}>Profanity in block-screen quotes</P>
          <Switch value={profanity} onValueChange={toggleProfanity} trackColor={{ true: colors.ok }} />
        </View>
      </Row>

      {status && (
        <Row>
          <P style={{ fontWeight: '700' }}>Protected mode — {isHardline ? 'LOCKIN HL (hardline)' : 'LOCKIN (play)'}</P>
          <Line label="Usage access" ok={status.usageAccess} />
          <Line label="Display over other apps" ok={status.overlay} />
          <Line label="Accessibility enabled" ok={status.accessibility} />
          <Line label="Accessibility connected" ok={status.accessibilityConnected} />
          <Line label="Battery unrestricted" ok={status.batteryUnrestricted} />
          {isHardline && <Line label="Settings guard (needs accessibility)" ok={status.accessibilityConnected} />}
          {isHardline && <Line label="Device admin (uninstall guard)" ok={status.deviceAdmin} />}
        </Row>
      )}

      {isHardline && status && (
        <Row>
          <P style={{ fontWeight: '700' }}>Device Owner</P>
          <Line label="LOCKIN HL is Device Owner" ok={status.deviceOwner} />
          <P muted>
            {status.deviceOwner
              ? 'During sessions: blocked apps are suspended, LOCKIN HL cannot be uninstalled, safe boot is disabled. All of it is reverted when the session ends or an emergency unlock completes.'
              : 'Optional, for a spare phone only. Set up with adb; see README → Device Owner. Without it, enforcement uses the accessibility guard and device admin.'}
          </P>
        </Row>
      )}

      <Button title="Permissions" kind="secondary" onPress={() => router.push('/onboarding/permissions')} />
      <Button title="Keep LOCKIN alive" kind="secondary" onPress={() => router.push('/keep-alive')} />
      <P muted>Device: {status?.manufacturer} · Android API {status?.sdkInt} · installer: {status?.installer ?? 'unknown (sideloaded)'}</P>
    </Screen>
  );
}

function Line({ label, ok }: { label: string; ok: boolean }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
      <P>{label}</P>
      <Status ok={ok} />
    </View>
  );
}
