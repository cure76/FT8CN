# Подготовка пакетов RDA

## Пакеты

| Пакет | Коды | Скрипт | Покрытие |
|-------|------|--------|----------|
| `mo_moscow` | MA-* / MO-* | `prepare_rda_pack.py` | 69/69 |
| `sm_smolensk` | SM-* | `prepare_sm_smolensk.py` | 29/29 |

## Запуск (Смоленская область)

```bash
cd tools/rda
source .venv/bin/activate
python prepare_sm_smolensk.py --merge-index ../../ft8cn/app/src/main/assets/rda
```

Aliases: `aliases_sm_smolensk.json`. Город Смоленск — три района `admin_level=9` (SM-01..03); `городской округ Смоленск` целиком пропускается.

## Москва + МО

```bash
python prepare_rda_pack.py
```

Кэш: `tools/rda/cache/`.

Приложение читает `assets/rda/index.json` и все `"enabled": true` пакеты.
