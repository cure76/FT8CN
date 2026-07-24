# TODO: работа FT8CN в фоне (аудио / CAT)

Статус: отложено. Вернуться после этапа RDA или при реальной необходимости «навигатор + FT8».

Дата фиксации: 2026-07-24  
Ветка/форк: FT8CN-RN3AOE (`com.rn3aoe.ft8cn`)

---

## Контекст

Приложение рассчитано на работу **на переднем плане**:

- `MainActivity` включает `FLAG_KEEP_SCREEN_ON`
- логика RX/TX/UTC живёт в `MainViewModel` + потоки (процесс), не в Activity
- **нет** Foreground Service для приёма/передачи FT8
- **нет** явного `AudioFocus`
- при уходе в другое приложение процесс *может* продолжать работу, но Android/OEM могут усыпить или убить его

Связанный уже сделанный кусок: Auto grid GPS останавливается в `MainActivity.onStop()` — в фоне локатор не обновляется.

---

## Текущее поведение (кратко)

| Подсистема | При уходе в фон |
|------------|-----------------|
| Микрофон / `HamRecorder` | Не останавливается Activity; риск потери микрофона / Doze |
| TX / `AudioTrack` | Может играть, пока жив процесс; без AudioFocus — риск срыва |
| VOX | Звук через динамик/гарнитуру телефона; конфликт с другим аудио |
| USB CAT | Не рвётся в `onStop`; обрыв при смерти процесса / exit |
| Bluetooth SPP | `BluetoothSerialService` (bound, не foreground); на части устройств режется в фоне |
| Wi‑Fi (Icom/Xiegu/Flex) | UDP/TCP в коннекторе; живут с процессом |
| PTT | Нет явного «снять PTT при паузе»; риск зависшего PTT при kill mid-TX |

Выход из приложения (`closeThisApp`) — disconnect CAT, stop listen, `System.exit(0)`.

---

## Цели (когда вернёмся)

1. Надёжный RX/TX при кратковременном уходе в другое приложение (карты, мессенджер).
2. Понятное уведомление «идёт FT8» (foreground service).
3. Безопасный PTT: при уходе в фон / kill — гарантированно OFF (насколько позволяет канал CAT).
4. Политика для Auto grid / будущего RDA: продолжать GPS в фоне или нет (отдельное решение + battery).

---

## Предлагаемые задачи

### A. Foreground Service для сессии FT8

- [ ] Добавить `FT8SessionService` (или аналог) с постоянным уведомлением
- [ ] Типы: `microphone` / при необходимости `connectedDevice` (API 34+)
- [ ] Разрешения в манифесте: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, …
- [ ] Старт при начале сессии (запись/декод), стоп при явном выходе пользователя
- [ ] Перенести удержание критичных ресурсов (mic, timer callbacks) под service, а не только под Activity

### B. AudioFocus

- [ ] `AudioManager.requestAudioFocus` на RX и на TX
- [ ] Обработка потери focus (пауза TX / индикация / не оставлять PTT ON)
- [ ] Согласовать с VOX vs сетевым аудио в радио

### C. CAT / PTT safety

- [ ] При `onStop` / потере focus / `onDestroy`: принудительный PTT OFF (CAT/RTS/DTR), если был ON
- [ ] Проверить Bluetooth path: нужен ли foreground для SPP на целевых прошивках
- [ ] Не вызывать `System.exit(0)` без гарантированного disconnect (или оставить, но сначала PTT off + disconnect)

### D. GPS / Auto grid / RDA в фоне

- [ ] Решить: трекинг локатора/RDA только при видимом UI или тоже в foreground service
- [ ] Если в фоне — отдельные интервалы и предупреждение о батарее
- [ ] Сейчас: `stopGridAutoUpdate()` в `onStop` — осознанно; менять только после A

### E. Проверка на устройстве

- [ ] USB CAT + VOX: свернуть в карты на 2–5 мин, убедиться в RX/TX
- [ ] Bluetooth CAT: то же
- [ ] Wi‑Fi radio: то же
- [ ] Звонок / другое аудио во время TX
- [ ] Doze / «оптимизация батареи» выкл/вкл

---

## Ключевые файлы

- `ft8cn/app/src/main/AndroidManifest.xml` — сервисы, permissions
- `ft8cn/app/src/main/java/com/bg7yoz/ft8cn/MainActivity.java` — lifecycle, KEEP_SCREEN_ON, grid stop
- `ft8cn/app/src/main/java/com/bg7yoz/ft8cn/MainViewModel.java` — recorder, timer, connectors
- `ft8cn/app/src/main/java/com/bg7yoz/ft8cn/wave/HamRecorder.java` / `MicRecorder.java`
- `ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ft8transmit/FT8TransmitSignal.java` — AudioTrack TX
- `ft8cn/app/src/main/java/com/bg7yoz/ft8cn/connector/*` — CAT / PTT
- `ft8cn/app/src/main/java/com/bg7yoz/ft8cn/bluetooth/BluetoothSerialService.java`

---

## Не делать в первой итерации возврата

- Полный «демон без UI» как у серверных приложений
- Смена архитектуры на отдельный процесс
- RDA lookup в фоне до стабильного GPS+foreground решения

---

## Заметки

- Для мобильной работы RN3AOE сценарий «карты + FT8» — главный драйвер этой TODO.
- Пока достаточно держать FT8CN на экране; этот документ — план усиления, не срочный баг.
