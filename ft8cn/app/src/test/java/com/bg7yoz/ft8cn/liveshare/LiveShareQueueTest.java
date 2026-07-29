package com.bg7yoz.ft8cn.liveshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LiveShareQueueTest {
    @Test
    public void chunkSplitsAt20() {
        List<Integer> items = new ArrayList<>();
        for (int index = 0; index < 45; index++) {
            items.add(index);
        }

        List<List<Integer>> chunks = LiveShareQueue.chunk(items, 20);

        assertEquals(3, chunks.size());
        assertEquals(20, chunks.get(0).size());
        assertEquals(20, chunks.get(1).size());
        assertEquals(5, chunks.get(2).size());
        assertEquals(Integer.valueOf(0), chunks.get(0).get(0));
        assertEquals(Integer.valueOf(44), chunks.get(2).get(4));
    }

    @Test
    public void flushOrdersPositionsBeforeQsos() throws IOException {
        InMemoryStore store = new InMemoryStore();
        LiveShareQueue queue = new LiveShareQueue(store);
        for (int index = 0; index < 21; index++) {
            queue.enqueue(LiveShareEventType.POSITION, "position-" + index, "{\"index\":" + index + "}");
        }
        queue.enqueue(LiveShareEventType.QSO, "qso-1", "{\"qso\":1}");
        List<String> calls = new ArrayList<>();

        int remaining = queue.flush(new LiveShareQueue.LiveShareClientSink() {
            @Override
            public void sendPositions(List<String> jsonBodies) {
                calls.add("positions:" + jsonBodies.size());
            }

            @Override
            public void sendQsos(List<String> jsonBodies) {
                calls.add("qsos:" + jsonBodies.size());
            }
        });

        assertEquals(Arrays.asList("positions:20", "positions:1", "qsos:1"), calls);
        assertEquals(0, remaining);
        assertEquals(0, queue.pendingCount());
    }

    @Test
    public void flushDrops422BatchAndContinues() throws IOException {
        InMemoryStore store = new InMemoryStore();
        LiveShareQueue queue = new LiveShareQueue(store);
        for (int index = 0; index < 21; index++) {
            queue.enqueue(LiveShareEventType.POSITION, "position-" + index, "{\"index\":" + index + "}");
        }
        queue.enqueue(LiveShareEventType.QSO, "qso-1", "{\"qso\":1}");
        List<String> calls = new ArrayList<>();

        int remaining = queue.flush(new LiveShareQueue.LiveShareClientSink() {
            @Override
            public void sendPositions(List<String> jsonBodies) throws IOException {
                calls.add("positions:" + jsonBodies.size());
                if (jsonBodies.contains("{\"index\":0}")) {
                    throw new LiveShareHttpException(422, "{\"error\":\"invalid payload\"}");
                }
            }

            @Override
            public void sendQsos(List<String> jsonBodies) {
                calls.add("qsos:" + jsonBodies.size());
            }

            @Override
            public void onDroppedClientPayload(int code, int eventCount) {
                calls.add("dropped:" + code + ":" + eventCount);
            }
        });

        assertEquals("positions:20", calls.get(0));
        assertEquals(1, calls.stream().filter("dropped:422:1"::equals).count());
        assertEquals(21, calls.stream().filter(call -> call.equals("positions:1")).count());
        assertEquals("qsos:1", calls.get(calls.size() - 1));
        assertEquals(0, remaining);
        assertEquals(0, queue.pendingCount());
    }

    @Test
    public void stopRunsAfter422BatchIsDropped() {
        InMemoryStore store = new InMemoryStore();
        LiveShareQueue queue = new LiveShareQueue(store);
        queue.enqueue(LiveShareEventType.POSITION, "bad-position", "{\"grid\":\"bad\"}");
        int[] stopCalls = {0};
        List<IOException> failures = new ArrayList<>();

        boolean stopped = LiveShareController.flushBestEffortThenStop(
                () -> queue.flush(new LiveShareQueue.LiveShareClientSink() {
                    @Override
                    public void sendPositions(List<String> jsonBodies) throws IOException {
                        throw new LiveShareHttpException(422, "invalid grid");
                    }

                    @Override
                    public void sendQsos(List<String> jsonBodies) {
                    }
                }),
                () -> stopCalls[0]++,
                failures::add);

        assertEquals(0, queue.pendingCount());
        assertEquals(1, stopCalls[0]);
        assertEquals(0, failures.size());
        assertTrue(stopped);
    }

    @Test
    public void stopRunsAfterNetworkFlushFailure() {
        int[] stopCalls = {0};
        List<IOException> failures = new ArrayList<>();

        boolean stopped = LiveShareController.flushBestEffortThenStop(
                () -> {
                    throw new IOException("offline");
                },
                () -> stopCalls[0]++,
                failures::add);

        assertEquals(1, stopCalls[0]);
        assertEquals(1, failures.size());
        assertEquals("offline", failures.get(0).getMessage());
        assertTrue(stopped);
    }

    @Test
    public void enqueueIgnoresDuplicateTypeAndClientEventId() {
        InMemoryStore store = new InMemoryStore();
        LiveShareQueue queue = new LiveShareQueue(store);

        queue.enqueue(LiveShareEventType.POSITION, "same-id", "{\"value\":1}");
        queue.enqueue(LiveShareEventType.POSITION, "same-id", "{\"value\":2}");
        queue.enqueue(LiveShareEventType.QSO, "same-id", "{\"value\":3}");

        assertEquals(2, queue.pendingCount());
    }

    private static final class InMemoryStore implements LiveShareQueue.Store {
        private final Map<String, LiveShareQueue.Event> events = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public void enqueue(LiveShareEventType type, String clientEventId, String jsonBody) {
            String key = type.name() + ":" + clientEventId;
            if (!events.containsKey(key)) {
                events.put(key, new LiveShareQueue.Event(
                        nextId++, type, clientEventId, jsonBody));
            }
        }

        @Override
        public List<LiveShareQueue.Event> list(LiveShareEventType type) {
            List<LiveShareQueue.Event> result = new ArrayList<>();
            for (LiveShareQueue.Event event : events.values()) {
                if (event.type == type) {
                    result.add(event);
                }
            }
            return result;
        }

        @Override
        public void delete(List<Long> ids) {
            events.values().removeIf(event -> ids.contains(event.id));
        }

        @Override
        public int pendingCount() {
            return events.size();
        }
    }
}
