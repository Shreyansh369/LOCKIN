import { ConfigContext, ExpoConfig } from 'expo/config';

// One codebase, two installable variants selected by APP_VARIANT.
// Pattern: https://docs.expo.dev/tutorial/eas/multiple-app-variants/
// (verified against expo/expo docs/pages/tutorial/eas/multiple-app-variants.mdx, SDK 58)
type Variant = 'play' | 'hardline';

const variant: Variant = process.env.APP_VARIANT === 'hardline' ? 'hardline' : 'play';
if (process.env.APP_VARIANT && !['play', 'hardline'].includes(process.env.APP_VARIANT)) {
  throw new Error(`APP_VARIANT must be "play" or "hardline", got "${process.env.APP_VARIANT}"`);
}
const isHardline = variant === 'hardline';

// Set by eas.json for every non-development profile. Dev clients keep INTERNET for Metro.
const blockNetwork = process.env.LOCKIN_BLOCK_NETWORK === '1';

// TODO(owner): replace CHANGEME with your reverse-domain before the first EAS build.
// Changing the package later means a new keystore and a fresh install.
const BASE_PACKAGE = 'com.CHANGEME.lockin';

// TODO(owner): set after `eas init`. One EAS project holds both packages (one keystore each).
const EAS_PROJECT_ID: string | undefined = undefined;

const iconSuffix = isHardline ? '-hardline' : '';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: isHardline ? 'LOCKIN HL' : 'LOCKIN',
  // Same slug for both: one EAS project (projectId is bound to a slug) holding two
  // application IDs, each with its own EAS-managed keystore.
  slug: 'lockin',
  // Distinct schemes so BlockerActivity deep links never resolve to the other variant.
  scheme: isHardline ? 'lockinhl' : 'lockin',
  version: '0.1.0',
  orientation: 'portrait',
  icon: `./assets/icon${iconSuffix}.png`,
  backgroundColor: '#0A0A0A',
  platforms: ['android'],
  android: {
    package: isHardline ? `${BASE_PACKAGE}.hardline` : BASE_PACKAGE,
    adaptiveIcon: {
      backgroundColor: '#0A0A0A',
      foregroundImage: `./assets/android-icon-foreground${iconSuffix}.png`,
      monochromeImage: `./assets/android-icon-monochrome${iconSuffix}.png`,
    },
    allowBackup: false,
    predictiveBackGestureEnabled: false,
    // Play restricts REQUEST_IGNORE_BATTERY_OPTIMIZATIONS to narrow use cases, so only hardline asks.
    // https://developer.android.com/training/monitoring-device-state/doze-standby#support_for_other_use_cases
    permissions: isHardline ? ['android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS'] : [],
    blockedPermissions: [
      'android.permission.READ_EXTERNAL_STORAGE',
      'android.permission.WRITE_EXTERNAL_STORAGE',
      'android.permission.VIBRATE',
      ...(blockNetwork
        ? ['android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE']
        : []),
    ],
  },
  plugins: [
    'expo-router',
    'expo-sqlite',
    ['./plugins/withLockin', { variant }],
  ],
  experiments: {
    typedRoutes: false,
  },
  extra: {
    variant,
    // `eas init` cannot edit a dynamic config; paste the projectId it prints into EAS_PROJECT_ID.
    eas: EAS_PROJECT_ID ? { projectId: EAS_PROJECT_ID } : undefined,
  },
});
