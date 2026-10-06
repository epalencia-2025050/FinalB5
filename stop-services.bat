@echo off
title Detener Microservicios - Delivery API
echo =======================================================
echo   Deteniendo todos los microservicios (Puertos 8000, 8081, 8082, 8083)...
echo =======================================================

for %%p in (8000 8081 8082 8083) do (
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%%p" ^| findstr "LISTENING"') do (
        echo Deteniendo proceso en puerto %%p (PID: %%a)...
        taskkill /F /PID %%a > nul 2>&1
    )
)

echo Microservicios detenidos exitosamente.
pause
