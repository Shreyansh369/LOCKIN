import { useEventListener } from 'expo';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { BackHandler, Text, View } from 'react-native';

import { ActiveSessionState, Focus } from '../../lib/focus';
import { formatDuration } from '../../lib/text';
import { Button, P, Row, Screen } from '../../ui/components';
import { colors } from '../../ui/theme';

export default function Session() {
  const [state, setState] = useState<ActiveSessionState | null>(null);
  const [now, setNow] = useState(Date.now());

  const refresh = useCallback(async () => {
    const s = await Focus.getSessionState();
    if (!s.active) {
      router.replace('/focus/setup');
      return;
    }
    setState(s);
  }, []);

  useFocusEffect(
    useCallback(() => {
      refresh();
      const id = setInterval(() => {
        setNow(Date.now());
        refresh();
      }, 1000);
      // Hardware back does nothing here; leaving goes through the exit ritual.
      const back = BackHandler.addEventListener('hardwareBackPress', () => true);
      return () => {
        clearInterval(id);
        back.remove();
      };
    }, [refresh])
  );

  useEventListener(Focus, 'onSessionEnded', () => router.replace('/focus/setup'));
  useEventListener(Focus, 'onBlocked', () => refresh());

  useEffect(() => setNow(Date.now()), [state]);

  if (!state) return <Screen><P>…</P></Screen>;

  const breakActive = state.breakUntil != null && state.breakUntil > now;
  const canBreak = state.mode !== 'strict' && state.breaksRemaining > 0 && !breakActive;

  return (
    <Screen>
      <P muted>LOCKED IN</P>
      <Text style={{ color: colors.text, fontSize: 30, fontWeight: '800' }}>{state.task}</Text>
      <Text style={{ color: colors.text, fontSize: 64, fontWeight: '200', fontVariant: ['tabular-nums'] }}>
        {formatDuration(state.endsAt - now)}
      </Text>

      <Row>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
          <P>Blocked attempts</P>
          <P style={{ fontWeight: '800' }}>{state.blockCount}</P>
        </View>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
          <P>Breaks left</P>
          <P style={{ fontWeight: '800' }}>{state.mode === 'strict' ? 'strict' : state.breaksRemaining}</P>
        </View>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
          <P>Allowed apps</P>
          <P style={{ fontWeight: '800' }}>{state.allowlist.length}</P>
        </View>
      </Row>

      {breakActive && (
        <Row>
          <P style={{ color: colors.warn, fontWeight: '700' }}>
            Break: {formatDuration(state.breakUntil! - now)} left, then everything relocks.
          </P>
        </Row>
      )}

      {state.emergencyUnlockAt != null && (
        <Row>
          <P style={{ color: colors.bad, fontWeight: '700' }}>
            Emergency unlock in {formatDuration(state.emergencyUnlockAt - now)}. Enforcement stays on until then.
          </P>
          <P muted>The phone dialer works the whole time.</P>
        </Row>
      )}

      <Button
        title="Take a break"
        kind="secondary"
        disabled={!canBreak}
        onPress={() => router.push({ pathname: '/exit', params: { intent: 'break' } })}
      />
      <Button title="Exit ritual" kind="secondary" onPress={() => router.push('/exit')} />
    </Screen>
  );
}
