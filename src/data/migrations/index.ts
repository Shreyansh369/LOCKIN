import m001 from './001_init';

// Append-only. Index + 1 is the schema version stored in PRAGMA user_version.
export const migrations: string[] = [m001];
