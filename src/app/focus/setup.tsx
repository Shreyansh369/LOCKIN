import { router } from 'expo-router';
import { useSQLiteContext } from 'expo-sqlite';
import { useEffect, useState } from 'react';
import { Alert, Pressable, Text, TextInput, View } from 'react-native';

import { lastAllowlist, syncNativeEvents } from '../../data/db';
import { Focus, LaunchableApp, SessionMode } from '../../lib/focus';
import { missingRequired, usePermissionStatus } from '../../lib/permissions';
import { wordCount } from '../../lib/text';
import { AppPicker } from '../../ui/AppPicker';
import { Button, H1, P, Screen, styles } from '../../ui/components';
import { colors, space } from '../../ui/theme';

const DURATIONS = [25, 50, 90, 120];

export default function Setup() {
  const db = useSQLiteContext();
  const { status } = usePermissionStatus();
  const [task, setTask] = useState('');
  const [duration, setDuration] = useState(50);
  const [custom, setCustom] = useState('');
  const [mode, setMode] = useState<SessionMode>('standard');
  const [apps, setApps] = useState<LaunchableApp[]>([]);
  const [allow, setAllow] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      // Drain first so the most recent session (and its allowlist) is in SQLite.
      await syncNativeEvents(db);
      const [list, last] = await Promise.all([Focus.listLaunchableApps(), lastAllowlist(db)]);
      setApps(list);
      const installed = new Set(list.map((a) => a.packageName));
      setAllow(new Set(last.filter((p) => installed.has(p))));
    })();
  }, [db]);

  const minutes = custom ? parseInt(custom, 10) : duration;
  const missing = status ? missingRequired(status) : ['…'];
  const taskOk = wordCount(task) >= 3;
  const durationOk = Number.isFinite(minutes) && minutes >= 1 && minutes <= 720;
  const canStart = taskOk && durationOk && missing.length === 0 && !busy;

  const start = async () => {
    setBusy(true);
    try {
      await Focus.startSession({ task: task.trim(), durationMin: minutes, allowlist: [...allow], mode });
      router.replace('/focus/session');
    } catch (e) {
      Alert.alert('Could not start', e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen>
      <H1>What are you doing?</H1>
      <TextInput
        style={styles.input}
        placeholder="e.g. write the methods section draft"
        placeholderTextColor={colors.muted}
        value={task}
        onChangeText={setTask}
        multiline
      />
      {!taskOk && task.length > 0 && <P muted>At least 3 words. Be specific.</P>}

      <P style={{ fontWeight: '700' }}>Duration (minutes)</P>
      <View style={{ flexDirection: 'row', gap: space(1), flexWrap: 'wrap' }}>
        {DURATIONS.map((d) => (
          <Chip key={d} label={String(d)} on={!custom && duration === d} onPress={() => (setCustom(''), setDuration(d))} />
        ))}
        <TextInput
          style={[styles.input, { minWidth: 90 }]}
          placeholder="custom"
          placeholderTextColor={colors.muted}
          keyboardType="number-pad"
          value={custom}
          onChangeText={(t) => setCustom(t.replace(/[^0-9]/g, ''))}
        />
      </View>

      <P style={{ fontWeight: '700' }}>Mode</P>
      <View style={{ flexDirection: 'row', gap: space(1) }}>
        <Chip label="Standard (2 breaks)" on={mode === 'standard'} onPress={() => setMode('standard')} />
        <Chip label="Strict (no breaks)" on={mode === 'strict'} onPress={() => setMode('strict')} />
      </View>

      <P style={{ fontWeight: '700' }}>Allowed apps ({allow.size})</P>
      <P muted>Everything else is blocked. Dialer, launcher, keyboard and Settings are always allowed.</P>
      <AppPicker apps={apps} selected={allow} onChange={setAllow} />

      {missing.length > 0 && status && (
        <Pressable onPress={() => router.push('/onboarding/permissions')}>
          <P style={{ color: colors.bad }}>Missing permissions: {missing.join(', ')} — tap to fix.</P>
        </Pressable>
      )}
      <Button title={busy ? 'Starting…' : `Lock in for ${durationOk ? minutes : '?'} min`} disabled={!canStart} onPress={start} />
      <Button title="Settings" kind="secondary" onPress={() => router.push('/settings')} />
    </Screen>
  );
}

function Chip({ label, on, onPress }: { label: string; on: boolean; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected: on }}
      style={{
        paddingVertical: space(1.25),
        paddingHorizontal: space(2),
        borderRadius: 6,
        borderWidth: 1,
        borderColor: on ? colors.text : colors.border,
        backgroundColor: on ? colors.text : colors.surface,
      }}
    >
      <Text style={{ color: on ? colors.bg : colors.text, fontWeight: '700', fontSize: 16 }}>{label}</Text>
    </Pressable>
  );
}
