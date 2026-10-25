import { Redirect } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { Focus } from '../lib/focus';
import { missingRequired } from '../lib/permissions';
import { colors } from '../ui/theme';

type Target = '/focus/session' | '/onboarding/permissions' | '/focus/setup';

export default function Index() {
  const [target, setTarget] = useState<Target | null>(null);
  useEffect(() => {
    (async () => {
      const state = await Focus.getSessionState();
      if (state.active) return setTarget('/focus/session');
      const status = await Focus.getPermissionStatus();
      setTarget(missingRequired(status).length ? '/onboarding/permissions' : '/focus/setup');
    })();
  }, []);
  if (!target) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg, justifyContent: 'center' }}>
        <ActivityIndicator color={colors.text} />
      </View>
    );
  }
  return <Redirect href={target} />;
}
