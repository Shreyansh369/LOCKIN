import { ReactNode } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextStyle, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors, space } from './theme';

export function Screen({ children, scroll = true }: { children: ReactNode; scroll?: boolean }) {
  return (
    <SafeAreaView style={styles.screen} edges={['top', 'bottom']}>
      {scroll ? (
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          {children}
        </ScrollView>
      ) : (
        <View style={[styles.content, { flex: 1 }]}>{children}</View>
      )}
    </SafeAreaView>
  );
}

export function H1({ children }: { children: ReactNode }) {
  return <Text style={styles.h1}>{children}</Text>;
}

export function P({ children, muted, style }: { children: ReactNode; muted?: boolean; style?: TextStyle }) {
  return <Text style={[styles.p, muted && { color: colors.muted }, style]}>{children}</Text>;
}

export function Button({
  title,
  onPress,
  disabled,
  kind = 'primary',
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  kind?: 'primary' | 'secondary' | 'danger';
}) {
  const bg = kind === 'primary' ? colors.text : kind === 'danger' ? colors.bad : colors.surface;
  const fg = kind === 'primary' ? colors.bg : colors.text;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      onPress={onPress}
      disabled={disabled}
      style={[styles.button, { backgroundColor: bg, opacity: disabled ? 0.35 : 1 }]}
    >
      <Text style={[styles.buttonText, { color: fg }]}>{title}</Text>
    </Pressable>
  );
}

export function Row({ children }: { children: ReactNode }) {
  return <View style={styles.row}>{children}</View>;
}

export function Status({ ok, label }: { ok: boolean; label?: string }) {
  return (
    <Text style={{ color: ok ? colors.ok : colors.bad, fontWeight: '700' }}>
      {label ?? (ok ? 'ON' : 'OFF')}
    </Text>
  );
}

export const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { padding: space(2), gap: space(2) },
  h1: { color: colors.text, fontSize: 28, fontWeight: '800' },
  p: { color: colors.text, fontSize: 16, lineHeight: 22 },
  button: { minHeight: 52, borderRadius: 6, justifyContent: 'center', paddingHorizontal: space(2) },
  buttonText: { fontSize: 17, fontWeight: '700', textAlign: 'center' },
  row: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 6,
    padding: space(2),
    gap: space(1),
  },
  input: {
    color: colors.text,
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 6,
    padding: space(1.5),
    fontSize: 17,
  },
});
