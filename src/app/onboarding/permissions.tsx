import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Modal, Text, View } from 'react-native';

import { ACCESSIBILITY_DISCLOSURE } from '../../lib/disclosure';
import { Focus, PermissionKind } from '../../lib/focus';
import { isHardline, missingRequired, requestNotifications, usePermissionStatus } from '../../lib/permissions';
import { Button, H1, P, Row, Screen, Status, styles } from '../../ui/components';
import { colors } from '../../ui/theme';

export default function PermissionsScreen() {
  const { status, refresh } = usePermissionStatus();
  const [disclosure, setDisclosure] = useState(false);

  // Re-check when returning from Settings (also covered by AppState in the hook).
  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  if (!status) return <Screen><P>Checking…</P></Screen>;

  const open = (kind: PermissionKind) => Focus.requestPermission(kind);
  const missing = missingRequired(status);
  const showRestricted = status.restrictedSettingsLikely;

  const onAccessibility = () => (isHardline ? open('accessibility') : setDisclosure(true));

  return (
    <Screen>
      <H1>Set up enforcement</H1>
      <P muted>
        Each switch lives in Android Settings. LOCKIN opens the right screen; come back here and the status updates.
      </P>

      <Row>
        <View style={rowHead}>
          <P style={bold}>Usage access {isHardline ? '(fallback)' : '(required)'}</P>
          <Status ok={status.usageAccess} />
        </View>
        <P muted>Lets LOCKIN see which app is in front by polling usage events every 0.5 s during a session.</P>
        {!status.usageAccess && <Button title="Open Usage access" onPress={() => open('usageAccess')} />}
      </Row>

      <Row>
        <View style={rowHead}>
          <P style={bold}>Display over other apps (required)</P>
          <Status ok={status.overlay} />
        </View>
        <P muted>Needed so the block screen can appear on top of the app you opened.</P>
        {!status.overlay && <Button title="Open overlay setting" onPress={() => open('overlay')} />}
      </Row>

      <Row>
        <View style={rowHead}>
          <P style={bold}>Accessibility {isHardline ? '(required)' : '(optional, faster)'}</P>
          <Status ok={status.accessibility} />
        </View>
        <P muted>
          {isHardline
            ? 'Primary detector. Also protects App info, Accessibility and Device admin settings during a session.'
            : 'Reports app switches instantly. Reads only the app package name.'}
        </P>
        {!status.accessibility && <Button title="Turn on accessibility" onPress={onAccessibility} />}
      </Row>

      {showRestricted && (
        <Row>
          <P style={{ ...bold, color: colors.warn }}>Toggle greyed out or “Restricted setting”?</P>
          <P muted>
            Android 13+ blocks accessibility for apps installed outside an app store until you allow it:
          </P>
          <P>1. Tap “Open App info” below.</P>
          <P>2. Tap ⋮ (top right) → “Allow restricted settings”, confirm with your PIN.</P>
          <P>3. Come back and tap “Turn on accessibility” again.</P>
          <P muted>
            If ⋮ has no such entry, first try to enable the service once (so Android shows the restricted dialog), then
            return to App info. On Android 15+ you may see an extra confirmation screen.
          </P>
          <Button title="Open App info" kind="secondary" onPress={() => open('appInfo')} />
        </Row>
      )}

      <Row>
        <View style={rowHead}>
          <P style={bold}>Notifications (recommended)</P>
          <Status ok={status.notifications} />
        </View>
        <P muted>Shows the running session and time left. Enforcement works without it, but you won't see it.</P>
        {!status.notifications && (
          <Button title="Allow notifications" onPress={() => requestNotifications(status.sdkInt).then(refresh)} />
        )}
      </Row>

      <Row>
        <View style={rowHead}>
          <P style={bold}>Battery & autostart (recommended)</P>
          <Status ok={status.batteryUnrestricted} />
        </View>
        <P muted>Stops the system from killing LOCKIN mid-session. {status.manufacturer} phones need extra steps.</P>
        <Button title="Keep LOCKIN alive" kind="secondary" onPress={() => router.push('/keep-alive')} />
      </Row>

      {isHardline && (
        <Row>
          <View style={rowHead}>
            <P style={bold}>Device admin (recommended)</P>
            <Status ok={status.deviceAdmin} />
          </View>
          <P muted>Blocks one-tap uninstall. Deactivate it in Device admin settings when no session is running.</P>
          {!status.deviceAdmin && <Button title="Activate device admin" onPress={() => open('deviceAdmin')} />}
        </Row>
      )}

      <Button
        title={missing.length ? 'Required permissions missing' : 'Continue'}
        disabled={missing.length > 0}
        onPress={() => router.replace('/focus/setup')}
      />

      <Modal visible={disclosure} animationType="none" transparent={false} onRequestClose={() => setDisclosure(false)}>
        <View style={[styles.screen, { padding: 16, gap: 16, justifyContent: 'center' }]}>
          <Text style={styles.h1}>How LOCKIN uses Accessibility</Text>
          {ACCESSIBILITY_DISCLOSURE.map((t) => (
            <P key={t}>{t}</P>
          ))}
          <Button
            title="I agree, open Accessibility settings"
            onPress={() => {
              setDisclosure(false);
              open('accessibility');
            }}
          />
          <Button title="No thanks" kind="secondary" onPress={() => setDisclosure(false)} />
        </View>
      </Modal>
    </Screen>
  );
}

const rowHead = { flexDirection: 'row' as const, justifyContent: 'space-between' as const, alignItems: 'center' as const };
const bold = { fontWeight: '700' as const };
