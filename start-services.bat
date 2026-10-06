@echo off
title Lanzador de Microservicios - Delivery API
echo =======================================================
echo   Iniciando Arquitectura de Microservicios Delivery
echo =======================================================
echo.

echo 1. Levantando auth-service en puerto 8081...
start "auth-service [Puerto 8081]" cmd /k "java -jar auth-service\target\auth-service-1.0.0.jar"

timeout /t 3 /nobreak > nul

echo 2. Levantando comercio-service en puerto 8082...
start "comercio-service [Puerto 8082]" cmd /k "java -jar comercio-service\target\comercio-service-1.0.0.jar"

timeout /t 3 /nobreak > nul

echo 3. Levantando pedido-service en puerto 8083...
start "pedido-service [Puerto 8083]" cmd /k "java -jar pedido-service\target\pedido-service-1.0.0.jar"

timeout /t 3 /nobreak > nul

echo 4. Levantando api-gateway en puerto 8000 (Punto Unico de Entrada)...
start "api-gateway [Puerto 8000]" cmd /k "java -jar api-gateway\target\api-gateway-1.0.0.jar"

echo.
echo =======================================================
echo   Todos los microservicios estan arrancando!
echo   Punto de Entrada en Postman / Evaluador:
echo   http://localhost:8000/api/v1/...
echo =======================================================
pause
