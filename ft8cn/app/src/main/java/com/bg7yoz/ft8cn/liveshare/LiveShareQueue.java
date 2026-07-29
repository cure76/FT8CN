package com.bg7yoz.ft8cn.liveshare;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

            try {
                send(type, sink, bodies);
            } catch (LiveShareHttpException error) {
                if (!isDroppableClientError(error.getCode())) {
                    throw error;
                }
                flushIndividually(type, events, sink);
                continue;
            }
            store.delete(ids);
        }
    }

    private void flushIndividually(
            LiveShareEventType type,
            List<Event> events,
            LiveShareClientSink sink) throws IOException {
        for (Event event : events) {
            List<Long> id = java.util.Collections.singletonList(event.id);
            try {
                send(type, sink, java.util.Collections.singletonList(event.jsonBody));
            } catch (LiveShareHttpException error) {
                if (!isDroppableClientError(error.getCode())) {
                    throw error;
                }
                store.delete(id);
                sink.onDroppedClientPayload(error.getCode(), 1);
                continue;
            }
            store.delete(id);
        }
    }

    private static void send(
            LiveShareEventType type,
            LiveShareClientSink sink,
            List<String> bodies) throws IOException {
        if (type == LiveShareEventType.POSITION) {
            sink.sendPositions(bodies);
        } else {
            sink.sendQsos(bodies);
        }
    }

    private static boolean isDroppableClientError(int code) {
        return code >= 400 && code < 500
                && code != 401 && code != 404 && code != 409 && code != 410;
    }

    public interface LiveShareClientSink {
        void sendPositions(List<String> jsonBodies) throws IOException;

        void sendQsos(List<String> jsonBodies) throws IOException;

        default void onDroppedClientPayload(int code, int eventCount) {
        }
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
