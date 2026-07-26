# TODO: загрузка пакетов RDA из внешнего источника

Статус: **реализовано (MVP)** в FT8CN-RN3AOE.

Дата фиксации: 2026-07-24  
Обновлено: 2026-07-26  
Ветка/форк: FT8CN-RN3AOE (`com.rn3aoe.ft8cn`)

---

## Как работает

1. Каталог: отдельный репозиторий [`cure76/ft8cn-rda-packs`](https://github.com/cure76/ft8cn-rda-packs)  
   URL: `https://cdn.jsdelivr.net/gh/cure76/ft8cn-rda-packs@main/catalog.json`  
   (зеркало также в [`rda-packs/`](../../rda-packs/catalog.json) этого репо)
2. В APK вшит только `mo_moscow` (`assets/rda/`).
3. Settings → **RDA packs**: Refresh / Download / Delete; лимит **3** скачанных пакета.
4. Файлы в `filesDir/rda/` + `local_index.json`; SHA-256 из каталога.
5. `RdaLookup` читает assets ∪ скачанное; после install/delete — `reload()`.

Локальная копия / публикация: репозиторий [`cure76/ft8cn-rda-packs`](https://github.com/cure76/ft8cn-rda-packs).

Публикация каталога:

```bash
python tools/rda/publish_catalog.py --packs-dir ../ft8cn-rda-packs \
  --base-url 'https://cdn.jsdelivr.net/gh/cure76/ft8cn-rda-packs@main/'
```
