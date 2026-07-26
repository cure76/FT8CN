# TODO: загрузка пакетов RDA из внешнего источника

Статус: **реализовано (MVP)** в FT8CN-RN3AOE.

Дата фиксации: 2026-07-24  
Обновлено: 2026-07-26  
Ветка/форк: FT8CN-RN3AOE (`com.rn3aoe.ft8cn`)

---

## Как работает

1. Каталог: [`rda-packs/catalog.json`](../../rda-packs/catalog.json) в этом репозитории  
   URL: `https://raw.githubusercontent.com/cure76/FT8CN/release/rda-packs/catalog.json`
2. В APK вшит только `mo_moscow` (`assets/rda/`).
3. Settings → **RDA packs**: Refresh / Download / Delete; лимит **3** скачанных пакета.
4. Файлы в `filesDir/rda/` + `local_index.json`; SHA-256 из каталога.
5. `RdaLookup` читает assets ∪ скачанное; после install/delete — `reload()`.

Локальная копия для будущего отдельного репо: `/Users/cure/virtual/github/ft8cn-rda-packs` (push после создания `cure76/ft8cn-rda-packs` на GitHub).

Публикация каталога:

```bash
python tools/rda/publish_catalog.py --packs-dir rda-packs \
  --base-url 'https://raw.githubusercontent.com/cure76/FT8CN/release/rda-packs/'
```
