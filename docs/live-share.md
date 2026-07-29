# Live share (FT8CN Tracker)

Live share sends the operator’s current grid/RDA, optional GPS track, working frequency, and completed QSOs to [ft8cn-tracker](https://github.com/cure76/ft8cn-tracker) while a session is active. Hunters follow the activation on a public share map.

**Design spec (tracker repo):** [`2026-07-29-ft8cn-live-share-design.md`](https://github.com/cure76/ft8cn-tracker/blob/main/docs/superpowers/specs/2026-07-29-ft8cn-live-share-design.md)

The app does **not** create sessions (`POST /sessions` is admin-only). The operator creates a session in the tracker admin UI and pastes the session token into FT8CN.

---

## Setup

1. In the tracker admin: create a session (TTL 24/48/72 h) and copy the **session token** (and share URL for announcements).
2. In FT8CN **Settings → Live share / FT8CN Tracker**:

| Field | Purpose |
|-------|---------|
| Enable live share | Master switch; no events are sent until **Start share** on the main screen |
| API base URL | e.g. `https://api.rn3aoe.ru` |
| Share base URL | Optional; default `http://track.rn3aoe.ru` — used to show the public share link |
| Callsign | Must match `OPERATOR_CALLSIGN` on the server |
| API key | `Authorization: Bearer …` |
| Session token | From admin client bundle |

3. Use **Test** to call `GET {api}/health` and/or verify snapshot access by token.
4. On the main screen: **Start share** / **Stop share** (visible when config is valid and Enable is on).

While sharing, FT8CN sends positions (grid/RDA, GPS cadence, `freq_hz`) and completed QSOs. Offline events are queued locally and flushed in batches (≤20 items, positions before QSOs). **Stop** flushes the queue (best effort), calls `POST …/stop`, and turns sharing off locally.

---

## Manual test plan

Run against a real or staging tracker instance and an admin-created session token.

1. **Admin create → token in FT8CN → Test OK.** Session exists; health/snapshot check succeeds with configured API key and token.
2. **Start → share map shows grid/RDA and freq in the header.** After Start, the public share page reflects the current locator and active frequency.
3. **Movement with GPS → track / speed.** With GPS fix and movement (≥60 s or ≥100 m), position updates appear on the map; server-derived speed updates when applicable.
4. **QSO → marker + list.** A completed QSO in FT8CN appears as a map marker and in the QSO list on the share page.
5. **Airplane mode → queue grows → network → flush batch, map catches up.** Events accumulate offline; after connectivity returns, batches drain and the map shows backlog positions/QSOs.
6. **Stop → status stopped; new QSO does not hit the API.** Stop closes the session on the server; further QSOs are not ingested for that token.
7. **Wrong api_key → 401, clear message.** Invalid credentials pause uploads and show an understandable error (uploads paused).

Optional: run unit tests under `com.bg7yoz.ft8cn.liveshare` (`./gradlew :app:testDebugUnitTest --tests 'com.bg7yoz.ft8cn.liveshare.*'`).

---

## Code layout

Package: `com.bg7yoz.ft8cn.liveshare` — config, HTTP client, offline queue, controller, and main-screen UI hooks. See the tracker spec §4 for component responsibilities and API mapping.
