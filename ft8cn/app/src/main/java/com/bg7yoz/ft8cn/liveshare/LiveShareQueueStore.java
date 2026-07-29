package com.bg7yoz.ft8cn.liveshare;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public final class LiveShareQueueStore implements LiveShareQueue.Store {
    private static final String TABLE = "live_share_queue";
    private static final String STATUS_PENDING = "pending";

    private final SQLiteDatabase database;

    public LiveShareQueueStore(SQLiteDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("database is required");
        }
        this.database = database;
        createTable();
    }

    @Override
    public void enqueue(
            LiveShareEventType type,
            String clientEventId,
            String jsonBody) {
        ContentValues values = new ContentValues();
        values.put("type", type.name().toLowerCase());
        values.put("client_event_id", clientEventId);
        values.put("json_body", jsonBody);
        values.put("created_at", System.currentTimeMillis());
        values.put("status", STATUS_PENDING);
        database.insertWithOnConflict(
                TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    @Override
    public List<LiveShareQueue.Event> list(LiveShareEventType type) {
        List<LiveShareQueue.Event> events = new ArrayList<>();
        try (Cursor cursor = database.query(
                TABLE,
                new String[]{"id", "client_event_id", "json_body"},
                "type = ? AND status = ?",
                new String[]{type.name().toLowerCase(), STATUS_PENDING},
                null,
                null,
                "created_at ASC, id ASC")) {
            int idColumn = cursor.getColumnIndexOrThrow("id");
            int eventIdColumn = cursor.getColumnIndexOrThrow("client_event_id");
            int bodyColumn = cursor.getColumnIndexOrThrow("json_body");
            while (cursor.moveToNext()) {
                events.add(new LiveShareQueue.Event(
                        cursor.getLong(idColumn),
                        type,
                        cursor.getString(eventIdColumn),
                        cursor.getString(bodyColumn)));
            }
        }
        return events;
    }

    @Override
    public void delete(List<Long> ids) {
        database.beginTransaction();
        try {
            for (Long id : ids) {
                database.delete(TABLE, "id = ?", new String[]{String.valueOf(id)});
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @Override
    public int pendingCount() {
        try (Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE status = ?",
                new String[]{STATUS_PENDING})) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private void createTable() {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "type TEXT NOT NULL,"
                        + "client_event_id TEXT NOT NULL,"
                        + "json_body TEXT NOT NULL,"
                        + "created_at INTEGER NOT NULL,"
                        + "status TEXT NOT NULL DEFAULT '" + STATUS_PENDING + "',"
                        + "UNIQUE(type, client_event_id))");
        database.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_live_share_queue_flush "
                        + "ON " + TABLE + " (type, status, created_at, id)");
    }
}
