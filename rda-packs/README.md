# FT8CN RDA packs

Offline GeoJSON packs for Russian District Award (RDA) lookup in FT8CN-RN3AOE.

Hosted in this repository under `rda-packs/` (raw GitHub URLs). A dedicated
`cure76/ft8cn-rda-packs` repo can mirror the same layout later.

## Catalog

- [`catalog.json`](catalog.json) — pack list with size, SHA-256, download paths
- [`packs/`](packs/) — GeoJSON (`rda_code` per feature)

Built-in APK pack: Moscow + Moscow Oblast (`mo_moscow`). App allows max **3** additional downloads.

Default catalog URL:

`https://raw.githubusercontent.com/cure76/FT8CN/release/rda-packs/catalog.json`

## Publish

```bash
python tools/rda/publish_catalog.py --packs-dir rda-packs \
  --base-url 'https://raw.githubusercontent.com/cure76/FT8CN/release/rda-packs/'
```
