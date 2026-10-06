#!/usr/bin/env bash
# ==============================================================================
# SCRIPT DE EVALUACI?N AUTOMATIZADA: SISTEMA DE DELIVERY & PEDIDOS (FinalB5)
# R?brica: Spring Security (25%), Transacciones (35%), JPA/BD (20%), API/QA (20%)
# ==============================================================================

# Configuraci?n de Gateway y Puertos
GATEWAY_PORT="${PORT:-8000}"
BASE_URL="${GATEWAY_URL:-http://localhost:${GATEWAY_PORT}}"

# Colores para salida de terminal
C_RESET="\033[0m"
C_BOLD="\033[1m"
C_GREEN="\033[32m"
C_RED="\033[31m"
C_YELLOW="\033[33m"
C_CYAN="\033[36m"
C_BLUE="\033[34m"

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

echo -e "${C_CYAN}${C_BOLD}"
echo "=============================================================================="
echo "    SUITE DE PRUEBAS DE INTEGRACI?N Y ESTR?S: SISTEMA FASTORDER DELIVERY     "
echo "=============================================================================="
echo -e "${C_RESET}"
echo -e "Target Base URL: ${C_BOLD}${BASE_URL}${C_RESET}"
echo "Iniciando auditor?a de endpoints y reglas de negocio..."
echo ""

# Helper para extraer campos JSON con verificaci?n real de int?rprete
extract_json() {
    local key="$1"
    local raw="$2"
    if command -v jq >/dev/null 2>&1; then
        echo "$raw" | jq -r ".${key} // empty"
    elif python -c "exit(0)" >/dev/null 2>&1; then
        python -c "import sys, json; data=json.loads(sys.argv[1]); print(data.get(sys.argv[2], ''))" "$raw" "$key" 2>/dev/null
    elif python3 -c "exit(0)" >/dev/null 2>&1; then
        python3 -c "import sys, json; data=json.loads(sys.argv[1]); print(data.get(sys.argv[2], ''))" "$raw" "$key" 2>/dev/null
    else
        echo "$raw" | sed -n -E "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"?([^,\"}]+)\"?.*/\1/p"
    fi
}

# Helper de aserci?n
assert_result() {
    local test_name="$1"
    local expected="$2"
    local actual="$3"
    local details="$4"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if [ "$expected" == "$actual" ]; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo -e "  [${C_GREEN}PASS${C_RESET}] ${test_name} (Status: ${actual})"
        return 0
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
        echo -e "  [${C_RED}FAIL${C_RESET}] ${test_name}"
        echo -e "         Esperado: ${C_YELLOW}${expected}${C_RESET} | Obtenido: ${C_RED}${actual}${C_RESET}"
        if [ -n "$details" ]; then
            echo -e "         Detalle: ${details}"
        fi
        return 1
    fi
}

# ------------------------------------------------------------------------------
# 0. VERIFICACI?N DE CONECTIVIDAD CON EL GATEWAY
# ------------------------------------------------------------------------------
echo -e "${C_BLUE}${C_BOLD}[0] Verificando estado del API Gateway...${C_RESET}"
HEALTH_CHECK=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/v1/comercios" || echo "000")
if [ "$HEALTH_CHECK" == "000" ]; then
    echo -e "${C_RED}[ERROR] No se pudo conectar a ${BASE_URL}.${C_RESET}"
    echo "Por favor verifique que los microservicios est?n iniciados ejecutando: start-services.bat"
    echo "O inicie cada servicio en su terminal respectiva."
    exit 1
fi
echo -e "${C_GREEN}Conexi?n con API Gateway exitosa (HTTP ${HEALTH_CHECK}).${C_RESET}\n"

# ------------------------------------------------------------------------------
# 1. AUTENTICACI?N Y SEGURIDAD JWT (25% R?BRICA)
# ------------------------------------------------------------------------------
echo -e "${C_BLUE}${C_BOLD}[1] M?DULO 1: AUTENTICACI?N Y SEGURIDAD JWT (25%)${C_RESET}"

# 1.1 Login ADMIN
RESP_ADMIN=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@delivery.com","password":"admin123"}')
BODY_ADMIN=$(echo "$RESP_ADMIN" | head -n -1)
STATUS_ADMIN=$(echo "$RESP_ADMIN" | tail -n 1)
TOKEN_ADMIN=$(extract_json token "$BODY_ADMIN")
assert_result "1.1 Login con ADMIN inicial" "200" "$STATUS_ADMIN" "$BODY_ADMIN"

# 1.2 Login REPARTIDOR
RESP_REP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"repartidor@delivery.com","password":"admin123"}')
BODY_REP=$(echo "$RESP_REP" | head -n -1)
STATUS_REP=$(echo "$RESP_REP" | tail -n 1)
TOKEN_REP=$(extract_json token "$BODY_REP")
assert_result "1.2 Login con REPARTIDOR inicial" "200" "$STATUS_REP" "$BODY_REP"

# 1.3 Login CLIENTE
RESP_CLI=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"cliente@delivery.com","password":"admin123"}')
BODY_CLI=$(echo "$RESP_CLI" | head -n -1)
STATUS_CLI=$(echo "$RESP_CLI" | tail -n 1)
TOKEN_CLI=$(extract_json token "$BODY_CLI")
assert_result "1.3 Login con CLIENTE inicial" "200" "$STATUS_CLI" "$BODY_CLI"

# 1.4 Registro p?blico nuevo usuario
TIMESTAMP=$(date +%s)
NUEVO_EMAIL="test_cliente_${TIMESTAMP}@delivery.com"
RESP_REG=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"nombre\":\"Cliente Prueba\",\"email\":\"${NUEVO_EMAIL}\",\"password\":\"pass123\",\"direccion\":\"Ciudad\",\"telefono\":\"55551234\"}")
BODY_REG=$(echo "$RESP_REG" | head -n -1)
STATUS_REG=$(echo "$RESP_REG" | tail -n 1)
assert_result "1.4 Registro p?blico nuevo usuario" "201" "$STATUS_REG" "$BODY_REG"

# 1.5 Prevenci?n de Escalamiento de Privilegios: intento de registro como ADMIN
HACK_EMAIL="hack_admin_${TIMESTAMP}@delivery.com"
RESP_HACK=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"nombre\":\"Hacker User\",\"email\":\"${HACK_EMAIL}\",\"password\":\"pass123\",\"rol\":\"ADMIN\"}")
BODY_HACK=$(echo "$RESP_HACK" | head -n -1)
ROL_HACK=$(extract_json rol "$BODY_HACK")
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if [ "$ROL_HACK" == "CLIENTE" ]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "  [${C_GREEN}PASS${C_RESET}] 1.5 Prevenci?n de escalamiento de privilegios (Rol asignado: ${ROL_HACK})"
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e "  [${C_RED}FAIL${C_RESET}] 1.5 Falla de seguridad: Se permiti? registrar con rol '${ROL_HACK}'"
fi

# 1.6 Registro con correo duplicado (debe retornar 409 Conflict)
RESP_DUP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"nombre\":\"Duplicado\",\"email\":\"${NUEVO_EMAIL}\",\"password\":\"pass123\"}")
STATUS_DUP=$(echo "$RESP_DUP" | tail -n 1)
assert_result "1.6 Registro con correo duplicado rechazado" "409" "$STATUS_DUP" ""

# 1.7 Login con contrase?a incorrecta (debe retornar 401 Unauthorized)
RESP_BAD_PASS=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@delivery.com","password":"clave_equivocada"}')
STATUS_BAD_PASS=$(echo "$RESP_BAD_PASS" | tail -n 1)
assert_result "1.7 Login con contrase?a incorrecta rechazado" "401" "$STATUS_BAD_PASS" ""

# 1.8 Acceso a endpoint administrativo sin token (debe retornar 401 o 403)
RESP_UNAUTH=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/comercios" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Ilegal","categoria":"RESTAURANTE"}')
STATUS_UNAUTH=$(echo "$RESP_UNAUTH" | tail -n 1)
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if [ "$STATUS_UNAUTH" == "401" ] || [ "$STATUS_UNAUTH" == "403" ]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "  [${C_GREEN}PASS${C_RESET}] 1.8 Endpoint protegido sin token rechazado (Status: ${STATUS_UNAUTH})"
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e "  [${C_RED}FAIL${C_RESET}] 1.8 Acceso no autorizado permiti? entrar con status ${STATUS_UNAUTH}"
fi
echo ""

# ------------------------------------------------------------------------------
# 2. GESTI?N DE COMERCIOS Y PRODUCTOS (20% R?BRICA)
# ------------------------------------------------------------------------------
echo -e "${C_BLUE}${C_BOLD}[2] M?DULO 2: GESTI?N DE COMERCIOS Y PRODUCTOS (20%)${C_RESET}"

# 2.1 Listar comercios p?blicos
RESP_LIST_COM=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/v1/comercios")
STATUS_LIST_COM=$(echo "$RESP_LIST_COM" | tail -n 1)
assert_result "2.1 Listar comercios p?blicos" "200" "$STATUS_LIST_COM" ""

# 2.2 Filtrar comercios por categor?a RESTAURANTE
RESP_FILT_COM=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/v1/comercios?categoria=RESTAURANTE")
STATUS_FILT_COM=$(echo "$RESP_FILT_COM" | tail -n 1)
assert_result "2.2 Filtrar comercios por categor?a" "200" "$STATUS_FILT_COM" ""

# 2.3 RBAC: CLIENTE intentando crear comercio (debe dar 403)
RESP_CLI_COM=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/comercios" \
  -H "Authorization: Bearer ${TOKEN_CLI}" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Restaurante de Cliente","categoria":"RESTAURANTE"}')
STATUS_CLI_COM=$(echo "$RESP_CLI_COM" | tail -n 1)
assert_result "2.3 RBAC: CLIENTE no puede crear comercio" "403" "$STATUS_CLI_COM" ""

# 2.4 ADMIN creando un nuevo comercio
RESP_NEW_COM=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/comercios" \
  -H "Authorization: Bearer ${TOKEN_ADMIN}" \
  -H "Content-Type: application/json" \
  -d "{\"nombre\":\"Parrillada Gourmet ${TIMESTAMP}\",\"categoria\":\"RESTAURANTE\",\"direccion\":\"Zona 10, Ciudad\",\"abierto\":true}")
BODY_NEW_COM=$(echo "$RESP_NEW_COM" | head -n -1)
STATUS_NEW_COM=$(echo "$RESP_NEW_COM" | tail -n 1)
COMERCIO_ID=$(extract_json id "$BODY_NEW_COM")
COMERCIO_ID="${COMERCIO_ID:-1}"
assert_result "2.4 ADMIN crea comercio exitosamente" "201" "$STATUS_NEW_COM" "$BODY_NEW_COM"

# 2.5 RBAC: CLIENTE intentando agregar producto (debe dar 403)
RESP_CLI_PROD=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/comercios/${COMERCIO_ID}/productos" \
  -H "Authorization: Bearer ${TOKEN_CLI}" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Infiltrado","precio":10.00,"stock":5}')
STATUS_CLI_PROD=$(echo "$RESP_CLI_PROD" | tail -n 1)
assert_result "2.5 RBAC: CLIENTE no puede agregar producto" "403" "$STATUS_CLI_PROD" ""

# 2.6 ADMIN agregando producto con stock controlado
RESP_NEW_PROD=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/comercios/${COMERCIO_ID}/productos" \
  -H "Authorization: Bearer ${TOKEN_ADMIN}" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Bife de Chorizo","precio":40.00,"stock":20,"disponible":true}')
BODY_NEW_PROD=$(echo "$RESP_NEW_PROD" | head -n -1)
STATUS_NEW_PROD=$(echo "$RESP_NEW_PROD" | tail -n 1)
PRODUCTO_ID=$(extract_json id "$BODY_NEW_PROD")
PRODUCTO_ID="${PRODUCTO_ID:-1}"
assert_result "2.6 ADMIN agrega producto con stock inicial (20 unidades)" "201" "$STATUS_NEW_PROD" "$BODY_NEW_PROD"

# 2.7 Listar productos del comercio
RESP_PRODS=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/v1/comercios/${COMERCIO_ID}/productos")
STATUS_PRODS=$(echo "$RESP_PRODS" | tail -n 1)
assert_result "2.7 Listar productos del comercio" "200" "$STATUS_PRODS" ""
echo ""

# ------------------------------------------------------------------------------
# 3. L?GICA TRANSACCIONAL DE PEDIDOS (35% R?BRICA)
# ------------------------------------------------------------------------------
echo -e "${C_BLUE}${C_BOLD}[3] M?DULO 3: L?GICA TRANSACCIONAL DE PEDIDOS (35%)${C_RESET}"

# 3.1 Crear Pedido como CLIENTE (2 unidades de Q40.00 = Subtotal Q80.00 + Q20.00 Env?o = Total Q100.00)
RESP_NEW_PED=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos" \
  -H "Authorization: Bearer ${TOKEN_CLI}" \
  -H "Content-Type: application/json" \
  -d "{\"items\":[{\"productoId\":${PRODUCTO_ID},\"cantidad\":2}]}")
BODY_NEW_PED=$(echo "$RESP_NEW_PED" | head -n -1)
STATUS_NEW_PED=$(echo "$RESP_NEW_PED" | tail -n 1)
PEDIDO_ID=$(extract_json id "$BODY_NEW_PED")
PEDIDO_ID="${PEDIDO_ID:-1}"
COSTO_ENVIO=$(extract_json costoEnvio "$BODY_NEW_PED")
MONTO_TOTAL=$(extract_json montoTotal "$BODY_NEW_PED")
ESTADO_PED=$(extract_json estado "$BODY_NEW_PED")

assert_result "3.1 Crear pedido como CLIENTE" "201" "$STATUS_NEW_PED" "$BODY_NEW_PED"

# 3.2 Validar c?lculo en servidor: Costo fijo de env?o Q20.00 y Total = Q100.00
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if [ "$COSTO_ENVIO" == "20" ] || [ "$COSTO_ENVIO" == "20.0" ] || [ "$COSTO_ENVIO" == "20.00" ]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "  [${C_GREEN}PASS${C_RESET}] 3.2.1 Costo de env?o calculado en servidor fijo: Q${COSTO_ENVIO}"
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e "  [${C_RED}FAIL${C_RESET}] 3.2.1 Costo de env?o err?neo: Q${COSTO_ENVIO} (esperado 20.00)"
fi

TOTAL_TESTS=$((TOTAL_TESTS + 1))
if [ "$MONTO_TOTAL" == "100" ] || [ "$MONTO_TOTAL" == "100.0" ] || [ "$MONTO_TOTAL" == "100.00" ]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "  [${C_GREEN}PASS${C_RESET}] 3.2.2 Monto total consistente calculado en servidor: Q${MONTO_TOTAL}"
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e "  [${C_RED}FAIL${C_RESET}] 3.2.2 Monto total err?neo: Q${MONTO_TOTAL} (esperado 100.00)"
fi

# 3.3 Verificar decremento de stock at?mico (20 - 2 = 18 restantes)
RESP_CHK_PROD=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/productos/${PRODUCTO_ID}")
STOCK_ACTUAL=$(extract_json stock "$RESP_CHK_PROD")
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if [ "$STOCK_ACTUAL" == "18" ]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "  [${C_GREEN}PASS${C_RESET}] 3.3 Descuento de stock at?mico verificado (Stock actual: ${STOCK_ACTUAL})"
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e "  [${C_RED}FAIL${C_RESET}] 3.3 Stock incorrecto tras crear pedido: ${STOCK_ACTUAL} (esperado 18)"
fi

# 3.4 Listar mis pedidos como CLIENTE
RESP_MIS_PED=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/v1/pedidos/mis-pedidos" \
  -H "Authorization: Bearer ${TOKEN_CLI}")
STATUS_MIS_PED=$(echo "$RESP_MIS_PED" | tail -n 1)
assert_result "3.4 Listar historial 'mis-pedidos' como CLIENTE" "200" "$STATUS_MIS_PED" ""

# 3.5 Listar pedidos disponibles como REPARTIDOR
RESP_DISP_PED=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/v1/pedidos/disponibles" \
  -H "Authorization: Bearer ${TOKEN_REP}")
STATUS_DISP_PED=$(echo "$RESP_DISP_PED" | tail -n 1)
assert_result "3.5 Listar pedidos 'disponibles' como REPARTIDOR" "200" "$STATUS_DISP_PED" ""
echo ""

# ------------------------------------------------------------------------------
# 4. ROLLBACK TRANSACCIONAL Y VALIDACI?N DE STOCK
# ------------------------------------------------------------------------------
echo -e "${C_BLUE}${C_BOLD}[4] M?DULO 4: ROLLBACK TRANSACCIONAL Y VALIDACI?N DE STOCK${C_RESET}"

# 4.1 Intentar crear pedido con stock excesivo (solicitar 50 cuando quedan 18)
RESP_OVER_PED=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos" \
  -H "Authorization: Bearer ${TOKEN_CLI}" \
  -H "Content-Type: application/json" \
  -d "{\"items\":[{\"productoId\":${PRODUCTO_ID},\"cantidad\":50}]}")
STATUS_OVER_PED=$(echo "$RESP_OVER_PED" | tail -n 1)
assert_result "4.1 Pedido con stock insuficiente rechazado con 400 Bad Request" "400" "$STATUS_OVER_PED" ""

# 4.2 Verificar que el stock se mantiene intacto tras el rollback compensatorio
RESP_CHK_PROD2=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/productos/${PRODUCTO_ID}")
STOCK_POST_ROLLBACK=$(extract_json stock "$RESP_CHK_PROD2")
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if [ "$STOCK_POST_ROLLBACK" == "18" ]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "  [${C_GREEN}PASS${C_RESET}] 4.2 Rollback ?ntegro: Stock preservado sin alteraciones (${STOCK_POST_ROLLBACK})"
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e "  [${C_RED}FAIL${C_RESET}] 4.2 Error de consistencia: Stock alterado a ${STOCK_POST_ROLLBACK} (esperado 18)"
fi
echo ""

# ------------------------------------------------------------------------------
# 5. M?QUINA DE ESTADOS ESTRICTA
# ------------------------------------------------------------------------------
echo -e "${C_BLUE}${C_BOLD}[5] M?DULO 5: M?QUINA DE ESTADOS ESTRICTA${C_RESET}"

# 5.1 Salto inv?lido: de PENDIENTE a ENTREGADO directamente (debe dar 400)
RESP_JUMP=$(curl -s -w "\n%{http_code}" -X PATCH "${BASE_URL}/api/v1/pedidos/${PEDIDO_ID}/estado" \
  -H "Authorization: Bearer ${TOKEN_REP}" \
  -H "Content-Type: application/json" \
  -d '{"nuevoEstado":"ENTREGADO"}')
STATUS_JUMP=$(echo "$RESP_JUMP" | tail -n 1)
assert_result "5.1 Salto inv?lido de estado rechazado (PENDIENTE -> ENTREGADO)" "400" "$STATUS_JUMP" ""

# 5.2 Transici?n 1: PENDIENTE -> EN_PREPARACION
RESP_T1=$(curl -s -w "\n%{http_code}" -X PATCH "${BASE_URL}/api/v1/pedidos/${PEDIDO_ID}/estado" \
  -H "Authorization: Bearer ${TOKEN_REP}" \
  -H "Content-Type: application/json" \
  -d '{"nuevoEstado":"EN_PREPARACION"}')
STATUS_T1=$(echo "$RESP_T1" | tail -n 1)
assert_result "5.2 Transici?n v?lida: PENDIENTE -> EN_PREPARACION" "200" "$STATUS_T1" ""

# 5.3 Transici?n 2: EN_PREPARACION -> EN_CAMINO
RESP_T2=$(curl -s -w "\n%{http_code}" -X PATCH "${BASE_URL}/api/v1/pedidos/${PEDIDO_ID}/estado" \
  -H "Authorization: Bearer ${TOKEN_REP}" \
  -H "Content-Type: application/json" \
  -d '{"nuevoEstado":"EN_CAMINO"}')
STATUS_T2=$(echo "$RESP_T2" | tail -n 1)
assert_result "5.3 Transici?n v?lida: EN_PREPARACION -> EN_CAMINO" "200" "$STATUS_T2" ""

# 5.4 Transici?n 3: EN_CAMINO -> ENTREGADO
RESP_T3=$(curl -s -w "\n%{http_code}" -X PATCH "${BASE_URL}/api/v1/pedidos/${PEDIDO_ID}/estado" \
  -H "Authorization: Bearer ${TOKEN_REP}" \
  -H "Content-Type: application/json" \
  -d '{"nuevoEstado":"ENTREGADO"}')
STATUS_T3=$(echo "$RESP_T3" | tail -n 1)
assert_result "5.4 Transici?n v?lida: EN_CAMINO -> ENTREGADO" "200" "$STATUS_T3" ""

# 5.5 Modificaci?n ilegal de pedido ya ENTREGADO
RESP_ILLEGAL=$(curl -s -w "\n%{http_code}" -X PATCH "${BASE_URL}/api/v1/pedidos/${PEDIDO_ID}/estado" \
  -H "Authorization: Bearer ${TOKEN_REP}" \
  -H "Content-Type: application/json" \
  -d '{"nuevoEstado":"EN_PREPARACION"}')
STATUS_ILLEGAL=$(echo "$RESP_ILLEGAL" | tail -n 1)
assert_result "5.5 Modificaci?n sobre pedido ENTREGADO rechazada" "400" "$STATUS_ILLEGAL" ""
echo ""

# ------------------------------------------------------------------------------
# 6. CANCELACI?N DE PEDIDOS Y RESTAURACI?N DE STOCK
# ------------------------------------------------------------------------------
echo -e "${C_BLUE}${C_BOLD}[6] M?DULO 6: CANCELACI?N DE PEDIDOS Y RESTAURACI?N DE STOCK${C_RESET}"

# 6.1 Crear pedido de prueba para cancelar (3 unidades: stock pasa de 18 a 15)
RESP_PED_CANCEL=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos" \
  -H "Authorization: Bearer ${TOKEN_CLI}" \
  -H "Content-Type: application/json" \
  -d "{\"items\":[{\"productoId\":${PRODUCTO_ID},\"cantidad\":3}]}")
BODY_PED_CANCEL=$(echo "$RESP_PED_CANCEL" | head -n -1)
STATUS_PED_CANCEL=$(echo "$RESP_PED_CANCEL" | tail -n 1)
ID_CANCELAR=$(extract_json id "$BODY_PED_CANCEL")
ID_CANCELAR="${ID_CANCELAR:-1}"
assert_result "6.1 Crear pedido de prueba para cancelaci?n" "201" "$STATUS_PED_CANCEL" ""

# 6.2 Cancelar pedido en estado PENDIENTE
RESP_DO_CANCEL=$(curl -s -w "\n%{http_code}" -X PATCH "${BASE_URL}/api/v1/pedidos/${ID_CANCELAR}/cancelar" \
  -H "Authorization: Bearer ${TOKEN_CLI}")
BODY_DO_CANCEL=$(echo "$RESP_DO_CANCEL" | head -n -1)
STATUS_DO_CANCEL=$(echo "$RESP_DO_CANCEL" | tail -n 1)
assert_result "6.2 Cancelar pedido en estado PENDIENTE" "200" "$STATUS_DO_CANCEL" "$BODY_DO_CANCEL"

# 6.3 Verificar reposici?n de stock (las 3 unidades deben haber regresado: 15 + 3 = 18)
RESP_RESTORED=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/productos/${PRODUCTO_ID}")
STOCK_RESTORED=$(extract_json stock "$RESP_RESTORED")
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if [ "$STOCK_RESTORED" == "18" ]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "  [${C_GREEN}PASS${C_RESET}] 6.3 Reposici?n de stock exitosa tras cancelaci?n (Stock restaurado: ${STOCK_RESTORED})"
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e "  [${C_RED}FAIL${C_RESET}] 6.3 Falla al reponer stock tras cancelaci?n: ${STOCK_RESTORED} (esperado 18)"
fi

# 6.4 Intentar cancelar el pedido que ya est? ENTREGADO (debe fallar con 400)
RESP_CANCEL_ENTREGADO=$(curl -s -w "\n%{http_code}" -X PATCH "${BASE_URL}/api/v1/pedidos/${PEDIDO_ID}/cancelar" \
  -H "Authorization: Bearer ${TOKEN_CLI}")
STATUS_CANCEL_ENTREGADO=$(echo "$RESP_CANCEL_ENTREGADO" | tail -n 1)
assert_result "6.4 Cancelaci?n de pedido ENTREGADO rechazada" "400" "$STATUS_CANCEL_ENTREGADO" ""
echo ""

# ------------------------------------------------------------------------------
# 7. PRUEBAS DE ESTR?S Y ALTA CONCURRENCIA
# ------------------------------------------------------------------------------
echo -e "${C_BLUE}${C_BOLD}[7] M?DULO 7: PRUEBA DE ESTR?S Y CONCURRENCIA ACID${C_RESET}"
echo "Creando producto exclusivo con stock limitado (5 unidades) para r?faga concurrente..."

RESP_CONC_PROD=$(curl -s -X POST "${BASE_URL}/api/v1/comercios/${COMERCIO_ID}/productos" \
  -H "Authorization: Bearer ${TOKEN_ADMIN}" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Oferta Flash","precio":15.00,"stock":5,"disponible":true}')
PROD_CONC_ID=$(extract_json id "$RESP_CONC_PROD")
PROD_CONC_ID="${PROD_CONC_ID:-1}"

echo "Lanzando 12 peticiones concurrentes de compra (1 unidad cada una) contra el producto ID ${PROD_CONC_ID}..."

CONC_OUT_DIR=$(mktemp -d 2>/dev/null || mktemp -d -t 'conc_test')
PIDS=()

for i in $(seq 1 12); do
    (
        CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos" \
          -H "Authorization: Bearer ${TOKEN_CLI}" \
          -H "Content-Type: application/json" \
          -d "{\"items\":[{\"productoId\":${PROD_CONC_ID},\"cantidad\":1}]}")
        echo "$CODE" > "${CONC_OUT_DIR}/res_${i}.txt"
    ) &
    PIDS+=($!)
done

# Esperar a que terminen todas las peticiones concurrentes
for pid in "${PIDS[@]}"; do
    wait "$pid" 2>/dev/null
done

EXITOS=0
RECHAZOS=0
for f in "${CONC_OUT_DIR}"/res_*.txt; do
    CODE=$(cat "$f" 2>/dev/null)
    if [ "$CODE" == "201" ]; then
        EXITOS=$((EXITOS + 1))
    elif [ "$CODE" == "400" ]; then
        RECHAZOS=$((RECHAZOS + 1))
    fi
done
rm -rf "$CONC_OUT_DIR"

# Verificar que exactamente 5 tuvieron ?xito y las dem?s 7 fueron rechazadas por stock agotado
RESP_FINAL_CONC=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/productos/${PROD_CONC_ID}")
STOCK_FINAL_CONC=$(extract_json stock "$RESP_FINAL_CONC")

TOTAL_TESTS=$((TOTAL_TESTS + 1))
if [ "$EXITOS" -eq 5 ] && [ "$STOCK_FINAL_CONC" -eq 0 ]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "  [${C_GREEN}PASS${C_RESET}] 7.1 Consistencia Concurrente: Exitosas: ${EXITOS}/5, Rechazadas por falta de stock: ${RECHAZOS}, Stock final: ${STOCK_FINAL_CONC}"
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e "  [${C_RED}FAIL${C_RESET}] 7.1 Inconsistencia en prueba concurrente. Exitosas: ${EXITOS}, Stock final: ${STOCK_FINAL_CONC} (Esperado: 5 exitosas, stock 0)"
fi
echo ""

# ------------------------------------------------------------------------------
# RESUMEN DE EJECUCI?N Y C?DIGO DE SALIDA
# ------------------------------------------------------------------------------
echo -e "${C_CYAN}${C_BOLD}"
echo "=============================================================================="
echo "                           RESUMEN DE AUDITOR?A FINAL                         "
echo "=============================================================================="
echo -e "${C_RESET}"
echo -e "Total de Pruebas Ejecutadas: ${C_BOLD}${TOTAL_TESTS}${C_RESET}"
echo -e "Pruebas Aprobadas:          ${C_GREEN}${C_BOLD}${PASSED_TESTS}${C_RESET}"
echo -e "Pruebas Fallidas:           ${C_RED}${C_BOLD}${FAILED_TESTS}${C_RESET}"
echo "------------------------------------------------------------------------------"

if [ "$FAILED_TESTS" -eq 0 ]; then
    echo -e "${C_GREEN}${C_BOLD}>>> RESULTADO: TODAS LAS PRUEBAS PASARON EXITOSAMENTE (100% CUMPLIMIENTO) <<<${C_RESET}"
    exit 0
else
    echo -e "${C_RED}${C_BOLD}>>> RESULTADO: SE ENCONTRARON FALLOS EN LA SUITE DE PRUEBAS <<<${C_RESET}"
    exit 1
fi
