import { router, useLocalSearchParams } from 'expo-router';
import { useSQLiteContext } from 'expo-sqlite';
import { useEffect, useMemo, useState } from 'react';
import { Alert, Pressable, Text, TextInput, View } from 'react-native';

import { ExitChoice, recentReasons, recordExit } from '../data/db';
import { ActiveSessionState, Focus, LaunchableApp } from '../lib/focus';
import { jaccard, wordCount } from '../lib/text';
import { AppPicker } from '../ui/AppPicker';
import { Button, H1, P, Row, Screen, styles } from '../ui/components';
import { colors, space } from '../ui/theme';

const WAIT_SECONDS = 45;
const MIN_WORDS = 15;
const MAX_SIMILARITY = 0.6;
const BREAK_MINUTES = [5, 10, 15];

export default function Exit() {
  const db = useSQLiteContext();
  const { intent } = useLocalSearchParams<{ intent?: string }>();
  const [session, setSession] = useState<ActiveSessionState | null | undefined>(undefined);
  const [left, setLeft] = useState(WAIT_SECONDS);
  const [reason, setReason] = useState('');
  const [history, setHistory] = useState<string[]>([]);
  const [breakMin, setBreakMin] = useState(5);
  const [apps, setApps] = useState<LaunchableApp[]>([]);
  const [breakApps, setBreakApps] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      const s = await Focus.getSessionState();
      setSession(s.active ? s : null);
      setHistory(await recentReasons(db, 10));
      setApps(await Focus.listLaunchableApps());
    })();
  }, [db]);

  useEffect(() => {
    if (left <= 0) return;
    const id = setTimeout(() => setLeft((n) => n - 1), 1000);
    return () => clearTimeout(id);
  }, [left]);

  const count = wordCount(reason);
  const tooSimilar = useMemo(
    () => history.find((h) => jaccard(reason, h) > MAX_SIMILARITY) ?? null,
    [reason, history]
  );
  const valid = left <= 0 && count >= MIN_WORDS && !tooSimilar;

  if (session === undefined) return <Screen><P>…</P></Screen>;
  if (session === null) {
    return (
      <Screen>
        <H1>No session running</H1>
        <Button title="Back" onPress={() => router.replace('/')} />
      </Screen>
    );
  }

  const now = Date.now();
  const breakActive = session.breakUntil != null && session.breakUntil > now;
  const canBreak = session.mode !== 'strict' && session.breaksRemaining > 0 && !breakActive;
  const emergencyRunning = session.emergencyUnlockAt != null;

  const act = async (choice: ExitChoice) => {
    setBusy(true);
    try {
      const text = reason.trim();
      if (choice === 'break') await Focus.requestBreak(breakMin, [...breakApps]);
      if (choice === 'end') await Focus.endSession(text);
      if (choice === 'emergency') await Focus.startEmergencyUnlock();
      await recordExit(db, session.sessionId, choice, text, count);
      router.replace(choice === 'end' ? '/focus/setup' : '/focus/session');
    } catch (e) {
      Alert.alert('Refused', e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen>
      <H1>Why are you quitting?</H1>
      <P muted>You committed to: {session.task}</P>

      {left > 0 ? (
        <Text style={{ color: colors.text, fontSize: 48, fontWeight: '200' }}>{left}s</Text>
      ) : (
        <P muted>Write it out. {MIN_WORDS}+ words, and not a copy of an old excuse.</P>
      )}

      <TextInput
        style={[styles.input, { minHeight: 140, textAlignVertical: 'top', opacity: left > 0 ? 0.4 : 1 }]}
        editable={left <= 0}
        multiline
        placeholder={left > 0 ? 'Sit with it first.' : 'Because…'}
        placeholderTextColor={colors.muted}
        value={reason}
        onChangeText={setReason}
      />
      <P muted>
        {count}/{MIN_WORDS} words
      </P>
      {tooSimilar && (
        <P style={{ color: colors.bad }}>Too close to a reason you already used: “{tooSimilar}”. Say something new or go back to work.</P>
      )}

      {history.length > 0 && (
        <Row>
          <P style={{ fontWeight: '700' }}>Your last reasons</P>
          {history.slice(0, 5).map((h, i) => (
            <P key={i} muted>
              • {h}
            </P>
          ))}
        </Row>
      )}

      <Button title="Back to work" onPress={() => router.replace('/focus/session')} />

      {canBreak && (
        <Row>
          <P style={{ fontWeight: '700' }}>Break ({session.breaksRemaining} left)</P>
          <View style={{ flexDirection: 'row', gap: space(1) }}>
            {BREAK_MINUTES.map((m) => (
              <Pressable
                key={m}
                onPress={() => setBreakMin(m)}
                style={{
                  padding: space(1.25),
                  borderRadius: 6,
                  borderWidth: 1,
                  borderColor: breakMin === m ? colors.text : colors.border,
                }}
              >
                <Text style={{ color: colors.text, fontWeight: '700' }}>{m} min</Text>
              </Pressable>
            ))}
          </View>
          <P muted>Apps to unlock ({breakApps.size || 'none selected = all apps'}):</P>
          <AppPicker apps={apps} selected={breakApps} onChange={setBreakApps} height={240} />
          <Button
            title={intent === 'break' ? `Take ${breakMin} min break` : `Break ${breakMin} min`}
            kind="secondary"
            disabled={!valid || busy}
            onPress={() => act('break')}
          />
        </Row>
      )}

      <Button title="End session" kind="danger" disabled={!valid || busy} onPress={() => act('end')} />
      {!emergencyRunning && (
        <Button
          title="Emergency unlock (15 min countdown)"
          kind="secondary"
          disabled={!valid || busy}
          onPress={() => act('emergency')}
        />
      )}
      <P muted>Need to call someone? The phone dialer is never blocked.</P>
    </Screen>
  );
}
