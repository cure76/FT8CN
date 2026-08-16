# Live share (FT8CN Tracker)

Live share sends the operator’s current grid/RDA, optional GPS track, working frequency, and completed QSOs to [ft8cn-tracker](https://github.com/cure76/ft8cn-tracker) while a session is active. Hunters follow the activation on a public share map.

**Design spec (tracker repo):** [`2026-07-29-ft8cn-live-share-design.md`](https://github.com/cure76/ft8cn-tracker/blob/main/docs/superpowers/specs/2026-07-29-ft8cn-live-share-design.md)

**Ad-hoc New session:** [`2026-07-30-ft8cn-new-session-design.md`](https://github.com/cure76/ft8cn-tracker/blob/main/docs/superpowers/specs/2026-07-30-ft8cn-new-session-design.md)

**Pause / resume (same session) + heartbeat:** [`2026-08-16-session-pause-resume-design.md`](https://github.com/cure76/ft8cn-tracker/blob/main/docs/superpowers/specs/2026-08-16-session-pause-resume-design.md), [`2026-08-16-session-heartbeat-design.md`](https://github.com/cure76/ft8cn-tracker/blob/main/docs/superpowers/specs/2026-08-16-session-heartbeat-design.md) — Stop → server `paused` (token kept); Start → `POST …/resume`; heartbeat ~60s while sharing.

---

## Setup

1. **Session token** — preferred for planned activations: in the tracker admin, create a session (TTL 24/48/72 h) and paste the **session token** into FT8CN.  
   **Ad-hoc:** in Settings → Live share, tap **New session (ad-hoc)** (`POST /api/v1/sessions` with your API key). Uses the server default TTL. Does **not** auto-start share.
2. In FT8CN **Settings → Live share / FT8CN Tracker**:

| Field | Purpose |
|-------|---------|
| Enable live share | Master switch; no events are sent until **Start share** on the main screen |
| API base URL | e.g. `https://api.rn3aoe.ru` |
| Share base URL | Optional; default `http://track.rn3aoe.ru` — used to show the public share link |
| Callsign | Must match an operator on the server |
| API key | `Authorization: Bearer …` |
| Session token | From admin, or from **New session (ad-hoc)** |

3. Use **Test** to call `GET {api}/health`.
4. On the main screen: **Start share** / **Stop share** (visible when config is valid and Enable is on).

While sharing, FT8CN sends positions (grid/RDA, GPS cadence, `freq_hz`) and completed QSOs. Offline events are queued locally and flushed in batches (≤20 items, positions before QSOs). **Stop** flushes the queue (best effort), calls `POST …/stop` (server → `paused`), turns sharing off locally, and **keeps** the session token. **Start** with a saved token calls `POST …/resume` (does not create a new session). While sharing, the app also sends `POST …/heartbeat` about every 60s so the map stays **Online** without new GPS/QSO.

If **New session** is tapped while already sharing, the app confirms, stops the current share, then creates and saves the new token.

---

## Manual test plan

Run against a real or staging tracker instance.

1. **Admin create → token in FT8CN → Test OK.** Session exists; health check succeeds.
2. **Ad-hoc New session → token + share URL update; Start still idle.** Token fills in; Start from main screen only.
3. **Start → share map shows grid/RDA and freq in the header.**
4. **Movement with GPS → track / speed.**
5. **QSO → marker + list.**
6. **Airplane mode → queue grows → network → flush batch.**
7. **Stop → server paused; token kept; new QSO does not hit the API.**
8. **Start again → resume same share URL; map continues the same session.**
9. *(optional)* **New session → new token; old share no longer used by the app.**
8. **Wrong api_key → 401, clear message.**
9. **New session while sharing → confirm → Stop → new token; old token unchanged on create failure.**

Optional: run unit tests under `com.bg7yoz.ft8cn.liveshare` (`./gradlew :app:testDebugUnitTest --tests 'com.bg7yoz.ft8cn.liveshare.*'`).

---

## Code layout

Package: `com.bg7yoz.ft8cn.liveshare` — config, HTTP client, offline queue, controller, and main-screen UI hooks. See the tracker spec §4 for component responsibilities and API mapping.
