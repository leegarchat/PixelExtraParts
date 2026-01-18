#!/bin/bash


stop_recording() {
    echo -e "\n[!] Останавливаю запись..."
    adb shell pkill -2 screenrecord
    sleep 1
}

if [ -z "$1" ]; then
    FILENAME="video_$(date +%Y%m%d_%H%M%S).mp4"
else
    FILENAME=$1
    [[ $FILENAME != *.mp4 ]] && FILENAME="$FILENAME.mp4"
fi

trap stop_recording SIGINT

echo "[*] Включаю отображение касаний..."
adb shell settings put system show_touches 1

echo "[*] Начинаю запись экрана: $FILENAME"
echo "[*] Для остановки нажмите Ctrl+C"

adb shell screenrecord --bit-rate 4M /sdcard/remote_record.mp4 &

RECORD_PID=$!
wait $RECORD_PID

trap - SIGINT

echo "[+] Копирую файл на компьютер..."
adb pull /sdcard/remote_record.mp4 "./$FILENAME"

echo "[*] Выключение отображение касаний..."
adb shell settings put system show_touches 0

if [ $? -eq 0 ]; then
    echo "[ВЫПОЛНЕНО] Файл сохранен как: $(pwd)/$FILENAME"
    adb shell rm /sdcard/remote_record.mp4
else
    echo "[ОШИБКА] Не удалось скопировать файл."
fi
