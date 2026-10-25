import { Stack } from 'expo-router';
import { SQLiteProvider, useSQLiteContext } from 'expo-sqlite';
import { StatusBar } from 'react-native';
import { useEffect } from 'react';
import { AppState } from 'react-native';

import { DB_NAME, migrate, syncNativeEvents } from '../data/db';
import { colors } from '../ui/theme';

/** Drains the native event queue on open and every time the app returns to the foreground. */
function NativeEventSync() {
  const db = useSQLiteContext();
  useEffect(() => {
    syncNativeEvents(db).catch(() => {});
    const sub = AppState.addEventListener('change', (s) => {
      if (s === 'active') syncNativeEvents(db).catch(() => {});
    });
    return () => sub.remove();
  }, [db]);
  return null;
}

export default function RootLayout() {
  return (
    <SQLiteProvider databaseName={DB_NAME} onInit={migrate}>
      <StatusBar barStyle="light-content" />
      <NativeEventSync />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.bg },
          headerTintColor: colors.text,
          contentStyle: { backgroundColor: colors.bg },
          animation: 'none',
        }}
      >
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="onboarding/permissions" options={{ title: 'Permissions' }} />
        <Stack.Screen name="focus/setup" options={{ title: 'New session' }} />
        <Stack.Screen name="focus/session" options={{ headerShown: false, gestureEnabled: false }} />
        <Stack.Screen name="exit" options={{ title: 'Exit ritual' }} />
        <Stack.Screen name="settings" options={{ title: 'Settings' }} />
        <Stack.Screen name="keep-alive" options={{ title: 'Keep LOCKIN alive' }} />
      </Stack>
    </SQLiteProvider>
  );
}
