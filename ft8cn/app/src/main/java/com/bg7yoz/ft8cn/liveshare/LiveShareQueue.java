package com.bg7yoz.ft8cn.liveshare;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

enum LiveShareEventType {
    POSITION,
    QSO
}

public final class LiveShareQueue {
    private static final int BATCH_SIZE = 20;

    private final Store store;

    public LiveShareQueue(Store store) {
        if (store == null) {
            throw new IllegalArgumentException("store is required");
        }
        this.store = store;
    }

    public void enqueue(
            LiveShareEventType type,
            String clientEventId,
            String jsonBody) {
        store.enqueue(type, clientEventId, jsonBody);
    }

    public int flush(LiveShareClientSink sink) throws IOException {
        flushType(LiveShareEventType.POSITION, sink);
        flushType(LiveShareEventType.QSO, sink);
        return store.pendingCount();
    }

    public int pendingCount() {
        return store.pendingCount();
    }

    static <T> List<List<T>> chunk(List<T> items, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("chunk size must be positive");
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < items.size(); start += size) {
            chunks.add(new ArrayList<>(
                    items.subList(start, Math.min(start + size, items.size()))));
        }
        return chunks;
    }

    private void flushType(LiveShareEventType type, LiveShareClientSink sink)
            throws IOException {
        for (List<Event> events : chunk(store.list(type), BATCH_SIZE)) {
            List<String> bodies = new ArrayList<>(events.size());
            List<Long> ids = new ArrayList<>(events.size());
            for (Event event : events) {
                bodies.add(event.jsonBody);
                ids.add(event.id);
            }

            if (type == LiveShareEventType.POSITION) {
                sink.sendPositions(bodies);
            } else {
                sink.sendQsos(bodies);
            }
            store.delete(ids);
        }
    }

    public interface LiveShareClientSink {
        void sendPositions(List<String> jsonBodies) throws IOException;

        void sendQsos(List<String> jsonBodies) throws IOException;
    }

    public interface Store {
        void enqueue(LiveShareEventType type, String clientEventId, String jsonBody);

        List<Event> list(LiveShareEventType type);

        void delete(List<Long> ids);

        int pendingCount();
    }

    public static final class Event {
        final long id;
        final LiveShareEventType type;
        final String clientEventId;
        final String jsonBody;

        public Event(
                long id,
                LiveShareEventType type,
                String clientEventId,
                String jsonBody) {
            this.id = id;
            this.type = type;
            this.clientEventId = clientEventId;
            this.jsonBody = jsonBody;
        }
    }
}
