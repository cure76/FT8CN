# Free-text grid+RDA после QSO (в эфир)

**Дата:** 2026-07-26  
**Статус:** реализовано  
**Форк:** FT8CN-RN3AOE  

## Цель

Опционально отправить **один** FT8 free-text слот после стандартного QSO, чтобы корреспондент увидел в декоде ваш 6-символьный локатор и (если есть) RDA.

## Правила

- По умолчанию **выкл.** (`txGridRdaAfterQso`)
- Формат ≤13: `KO85EO MO-84` или только `KO85EO`
- Данные из снимка QSO (`myMaidenGridAtStart` / `myRdaAtStart`)
- Arm в `doComplete`, TX после `resetToCQ` (после RR73/73), затем обычный CQ
- Без ожидания ACK; не mid-QSO

## Реализация

- Settings: switch **TX grid+RDA after QSO** → `GeneralVariables.txGridRdaAfterQso` / DB key `txGridRdaAfterQso`
- `FT8TransmitSignal.buildPostQsoFreeText` + `pendingPostQsoFreeText` / one-shot arm in `resetToCQ`
- `DoTransmitRunnable` шлёт `i3=0,n3=0`; `afterPlayAudio` → CQ
- Не трогает COMMENT / ADIF

## Ручная проверка (на воздухе)

1. Settings → включить **TX grid+RDA after QSO** (нужна 6-символьная сетка)
2. Завершить обычное QSO до RR73/73
3. На следующем своём слоте должен уйти free text `GRID` или `GRID RDA`, затем CQ
4. Выкл. опции — лишнего слота нет
5. Второй приёмник / WSJT-X: free text виден в декоде

См. план: free-text grid+RDA после QSO.
