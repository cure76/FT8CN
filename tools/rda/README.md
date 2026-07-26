# Подготовка пакетов RDA

## Пакеты

| Пакет | Коды | Скрипт | Где |
|-------|------|--------|-----|
| `mo_moscow` | MA-* / MO-* | `prepare_rda_pack.py` | APK assets + `rda-packs/` |
| `sm_smolensk` | SM-* | `prepare_sm_smolensk.py` | только `rda-packs/` (скачивание) |

## Каталог для приложения

```bash
python tools/rda/publish_catalog.py --packs-dir rda-packs \
  --base-url 'https://raw.githubusercontent.com/cure76/FT8CN/release/rda-packs/'
```

Скопируйте готовый `.geojson` в `rda-packs/packs/`, затем пересоберите `catalog.json`.

## Запуск (Смоленская область)

```bash
cd tools/rda
source .venv/bin/activate
python prepare_sm_smolensk.py
# затем скопировать out/sm_smolensk.geojson → rda-packs/packs/ и publish_catalog.py
```

## Москва + МО

```bash
python prepare_rda_pack.py
# обновить assets/rda/mo_moscow.geojson и rda-packs/packs/mo_moscow.geojson
```

Кэш: `tools/rda/cache/`.

Приложение: builtin из `assets/rda/index.json`; остальные — Download в Settings.
