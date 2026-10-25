import type { SQLiteDatabase } from 'expo-sqlite';

import { Focus } from '../lib/focus';
import { migrations } from './migrations';

export const DB_NAME = 'lockin.db';

/** SQLiteProvider onInit. https://docs.expo.dev/versions/v58.0.0/sdk/sqlite/ */
export async function migrate(db: SQLiteDatabase): Promise<void> {
  await db.execAsync('PRAGMA journal_mode = WAL;');
  const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
  let version = row?.user_version ?? 0;
  while (version < migrations.length) {
    const sql = migrations[version];
    const next = version + 1;
    await db.withExclusiveTransactionAsync(async (txn) => {
      await txn.execAsync(sql);
      await txn.execAsync(`PRAGMA user_version = ${next}`);
    });
    version = next;
  }
}

/**
 * Moves the native event queue (written while JS may have been dead) into SQLite.
 * The native queue is cleared on drain, so events are inserted in one transaction.
 */
export async function syncNativeEvents(db: SQLiteDatabase): Promise<void> {
  const events = await Focus.drainEvents();
  if (events.length === 0) return;
  await db.withExclusiveTransactionAsync(async (txn) => {
    for (const e of events) {
      switch (e.type) {
        case 'block':
          await txn.runAsync(
            'INSERT INTO block_events (session_id, ts, pkg, reason, source) VALUES (?, ?, ?, ?, ?)',
            e.sessionId ?? null,
            e.ts,
            e.pkg,
            e.reason,
            e.source
          );
          break;
        case 'session_start':
          await txn.runAsync(
            `INSERT OR IGNORE INTO sessions (id, task, mode, allowlist, started_at, planned_end_at)
             VALUES (?, ?, ?, ?, ?, ?)`,
            e.sessionId,
            e.task,
            e.mode,
            e.allowlist,
            e.startedAt,
            e.endsAt
          );
          break;
        case 'session_end':
          await txn.runAsync(
            'UPDATE sessions SET ended_at = ?, end_type = ? WHERE id = ?',
            e.ts,
            e.endType,
            e.sessionId
          );
          break;
        // break_start / break_end / emergency_start: the exit ritual already stored the
        // user's reason in exit_events; stats (Phase 2) will read these from the queue too.
        default:
          break;
      }
    }
  });
}

export async function lastAllowlist(db: SQLiteDatabase): Promise<string[]> {
  const row = await db.getFirstAsync<{ allowlist: string }>(
    'SELECT allowlist FROM sessions ORDER BY started_at DESC LIMIT 1'
  );
  return row?.allowlist ? row.allowlist.split(',').filter(Boolean) : [];
}

export type ExitChoice = 'break' | 'end' | 'emergency';

export async function recentReasons(db: SQLiteDatabase, limit: number): Promise<string[]> {
  const rows = await db.getAllAsync<{ reason: string }>(
    'SELECT reason FROM exit_events ORDER BY ts DESC LIMIT ?',
    limit
  );
  return rows.map((r) => r.reason);
}

export async function recordExit(
  db: SQLiteDatabase,
  sessionId: string | null,
  choice: ExitChoice,
  reason: string,
  wordCount: number
): Promise<void> {
  await db.runAsync(
    'INSERT INTO exit_events (session_id, ts, choice, reason, word_count) VALUES (?, ?, ?, ?, ?)',
    sessionId,
    Date.now(),
    choice,
    reason,
    wordCount
  );
}
