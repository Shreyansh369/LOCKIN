import { useMemo, useState } from 'react';
import { FlatList, Image, Pressable, Text, TextInput, View } from 'react-native';

import type { LaunchableApp } from '../lib/focus';
import { styles } from './components';
import { colors, space } from './theme';

/** Searchable multi-select over launchable apps. Always-allowed apps are shown but locked on. */
export function AppPicker({
  apps,
  selected,
  onChange,
  height = 360,
}: {
  apps: LaunchableApp[];
  selected: Set<string>;
  onChange: (next: Set<string>) => void;
  height?: number;
}) {
  const [query, setQuery] = useState('');
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = q
      ? apps.filter((a) => a.label.toLowerCase().includes(q) || a.packageName.includes(q))
      : apps;
    // Selected first so the current allowlist is visible at a glance.
    return [...list].sort((a, b) => Number(selected.has(b.packageName)) - Number(selected.has(a.packageName)));
  }, [apps, query, selected]);

  const toggle = (pkg: string) => {
    const next = new Set(selected);
    if (next.has(pkg)) next.delete(pkg);
    else next.add(pkg);
    onChange(next);
  };

  return (
    <View style={{ gap: space(1) }}>
      <TextInput
        style={styles.input}
        placeholder="Search apps"
        placeholderTextColor={colors.muted}
        value={query}
        onChangeText={setQuery}
        autoCorrect={false}
      />
      <View style={{ height, borderColor: colors.border, borderWidth: 1, borderRadius: 6 }}>
        <FlatList
          data={filtered}
          keyExtractor={(a) => a.packageName}
          nestedScrollEnabled
          keyboardShouldPersistTaps="handled"
          renderItem={({ item }) => {
            const on = item.alwaysAllowed || selected.has(item.packageName);
            return (
              <Pressable
                disabled={item.alwaysAllowed}
                onPress={() => toggle(item.packageName)}
                accessibilityRole="checkbox"
                accessibilityState={{ checked: on, disabled: item.alwaysAllowed }}
                style={{ flexDirection: 'row', alignItems: 'center', padding: space(1), gap: space(1.5) }}
              >
                {item.icon ? (
                  <Image source={{ uri: item.icon }} style={{ width: 32, height: 32 }} />
                ) : (
                  <View style={{ width: 32, height: 32 }} />
                )}
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.text, fontSize: 16 }}>{item.label}</Text>
                  {item.alwaysAllowed && <Text style={{ color: colors.muted, fontSize: 12 }}>Always allowed</Text>}
                </View>
                <Text style={{ color: on ? colors.ok : colors.muted, fontSize: 20, fontWeight: '800' }}>
                  {on ? '✓' : '○'}
                </Text>
              </Pressable>
            );
          }}
        />
      </View>
    </View>
  );
}
