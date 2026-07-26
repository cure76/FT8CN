# RDA downloadable packs (GitHub catalog)

**Дата:** 2026-07-26  
**Статус:** реализовано (MVP)

## Решения

- Каталог и GeoJSON: репозиторий [`cure76/ft8cn-rda-packs`](https://github.com/cure76/ft8cn-rda-packs) (jsDelivr); зеркало `rda-packs/` в FT8CN
- В APK: только `mo_moscow`
- Max **3** downloaded packs; builtin не считается
- SHA-256 обязателен; QSO lookup офлайн

## Код

- [`RdaPackManager.java`](../../ft8cn/app/src/main/java/com/bg7yoz/ft8cn/rda/RdaPackManager.java)
- [`RdaLookup.java`](../../ft8cn/app/src/main/java/com/bg7yoz/ft8cn/rda/RdaLookup.java)
- Settings: секция RDA packs в Config

## Tools

Pack authors: https://github.com/cure76/ft8cn-rda-packs/tree/main/tools
