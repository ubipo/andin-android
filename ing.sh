#!/bin/bash
adb shell am force-stop MyING.be
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
sleep 1
adb shell input keyevent "KEYCODE_TAB"
adb shell input keyevent "KEYCODE_HOME"
adb shell input keyevent "KEYCODE_ENTER"
sleep 7
adb shell input keyevent "KEYCODE_TAB"
adb shell input keyevent "KEYCODE_TAB"
adb shell input keyevent "KEYCODE_ENTER"
sleep 1
#adb shell input