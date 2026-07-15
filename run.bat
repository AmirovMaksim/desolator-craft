@echo off
REM ============================================================
REM  DESOLATOR CRAFT - запуск игры (Windows)
REM  Двойной клик по этому файлу запустит игру.
REM  Требуется: Java 17+ (проверь: java -version)
REM ============================================================
setlocal

REM Папка с локальным Maven-кэшем (.m2)
set M2=%USERPROFILE%\.m2\repository

REM Собираем classpath: все jme3 + прочие jar, кроме "лишних" версий LWJGL.
REM jMonkeyEngine 3.6.1 требует ТОЛЬКО LWJGL 3.3.2 — иначе
REM "Incompatible Java and native library versions".
set CP=
for /r "%M2%" %%f in (*.jar) do (
    set "J=%%~f"
    set "LOW=%%~nxf"
    call :filter "%%~f" "%%~nxf"
)
REM добавляем скомпилированные классы (target/classes — приоритет)
if exist target\classes set "CP=target\classes;%CP%"

echo Запуск DESOLATOR CRAFT...
echo (при вылете см. файл dc_crash.log рядом с этим bat)
java --enable-native-access=ALL-UNNAMED -cp "%CP%" com.mygame.Main
set RC=%ERRORLEVEL%
echo.
echo [Процесс завершён, код %RC%]
if not "%RC%"=="0" (
    echo ОШИБКА. Подробности в dc_crash.log
)
echo Нажмите любую клавишу, чтобы закрыть окно...
pause >nul
goto :eof

:filter
set "F=%~1"
set "N=%~2"
REM пропускаем natives-джары из classpath (JME достанет .dll сам)
echo "%F%" | findstr /i "natives" >nul && goto :eof
REM пропускаем всё, кроме lwjgl 3.3.2 (чтобы не было конфликта версий)
echo "%F%" | findstr /i "\lwjgl\" >nul
if not errorlevel 1 (
    echo "%N%" | findstr /i "3.3.2" >nul
    if errorlevel 1 goto :eof
)
set "CP=%CP%;%F%"
goto :eof
