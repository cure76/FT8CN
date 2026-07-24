# RDA COMMENT — план реализации

**Дата:** 2026-07-24  
**Спека:** `docs/superpowers/specs/2026-07-24-rda-comment-design.md`  
**Цель:** вшить пакет Москва+МО и писать свой RDA в ADIF `COMMENT` при Auto grid.

## Файлы

| Файл | Роль |
|------|------|
| `ft8cn/app/src/main/assets/rda/index.json` | Индекс пакетов |
| `ft8cn/app/src/main/assets/rda/mo_moscow.geojson` | Полигоны MA/MO |
| `…/rda/RdaLookup.java` | Загрузка assets + point-in-polygon |
| `GeneralVariables.java` | `lastKnownLat/Lon` при Auto grid |
| `MainActivity.java` | Сохранять координаты; init lookup |
| `FT8TransmitSignal.java` | `myRdaAtStart` вместе с сеткой |
| `log/QSLRecord.java` | Формат COMMENT с опциональным RDA |

## Задачи

1. Скопировать `tools/rda/out/{index.json,mo_moscow.geojson}` → `assets/rda/` (extras раньше крупных округов — меньший bbox побеждает в lookup).
2. `RdaLookup`: lazy load, bbox + ray-cast, сортировка по площади bbox ↑.
3. GPS → `GeneralVariables.setLastKnownLocation`; выкл Auto grid → clear.
4. Старт QSO → `myRdaAtStart = lookup(...)` только если Auto grid ON и есть fix.
5. `QSLRecord`: `Distance…, RDA: XX-NN, QSO by FT8CN` / без RDA как сейчас.
6. Сборка debug APK для проверки компиляции.

## Проверка

- Точка в Кремле → `MA-07`; в Подольске → `MO-*`; вне МО → `""`.
- Auto grid OFF → нет `RDA:` в comment.
