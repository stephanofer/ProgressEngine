# Diseño Final: Pagos, Presentación Monetaria y Confirmación

Este documento es la especificación formal del bloque de pagos de ProgressEngine. Consolida el diseño inicial, las correcciones posteriores y las decisiones aprobadas para implementación. Si una implementación contradice este documento, debe corregirse o elevarse como una nueva decisión de producto.

## Resultado Esperado

ProgressEngine debe permitir enviar points con cantidades abreviadas, presentar montos de forma coherente en toda la experiencia, colorear montos por magnitud y pedir una confirmación visual segura para transferencias grandes.

La economía sigue siendo una sola economía global de `points`, almacenada como `long`, con rango `0..maximumBalance`.

## Decisiones Fundamentales

| Tema | Decisión |
| --- | --- |
| Modelo económico | `long`; no hay balances ni transferencias decimales persistidas. |
| Mínimo de pay | Entero positivo. `0.1` no es válido como configuración de `pay.minimum`. |
| Límites | `pay.minimum` y `pay.maximum` existentes; se aplican al monto final expandido. |
| Habilitación de pay | Se usa exclusivamente `availability.pay`; no se agrega `enable-pay`. |
| Confirmación | Umbral inclusivo: `amount >= threshold`. |
| UI de confirmación | API nativa Dialog de Paper, no zMenu. |
| Acción de confirmar | Callback Paper directo; no simular un comando del jugador. |
| Seguridad | Token persistido, un solo uso, expiración corta, emisor/receptor/monto/revisión ligados. |
| Revisión | La revisión esperada se valida dentro de la transacción MySQL tras bloquear la cuenta. |
| Formato numérico | Raw, con separadores y compacto, deterministas y localizados. |
| Color | Solo en `Component` de presentación; nunca en valores persistidos ni placeholders numéricos. |

## Cantidades de Entrada

### Sintaxis

Los comandos monetarios aceptan un entero sin signo o un decimal sin signo seguido de un sufijo configurado:

```text
500
10k
1.5k
2M
0.1k
```

Los sufijos son case-insensitive.

```text
10k = 10K = 10k
```

### Expansión exacta

El parser separa número y sufijo, multiplica con aritmética decimal exacta y exige que el resultado final sea un entero representable como `long`.

| Entrada | Resultado |
| --- | ---: |
| `10k` | `10000` |
| `1.5k` | `1500` |
| `0.1k` | `100` |
| `0.001k` | `1` |
| `1.0001k` | Rechazada: resultado no entero |
| `1.5` | Rechazada: los decimales requieren sufijo |
| `1e3` | Rechazada |
| `1,000` | Rechazada |
| `-1k` | Rechazada |

No se usa `double`. Un overflow, un sufijo desconocido, una fracción residual, cero donde se requiere una cantidad positiva o un valor superior al límite se rechazan antes de tocar la economía.

### Alcance

Los atajos aplican a:

```text
/points pay <player> <amount>
/points admin add <player|uuid> <amount>
/points admin remove <player|uuid> <amount>
/points admin set <player|uuid> <amount>
```

No aplican a páginas, tiempos, UUIDs, razones ni API Java. La API pública continúa recibiendo `long`.

### Configuración

```yaml
amount-input:
  multipliers:
    k: 1000
    m: 1000000
    b: 1000000000
    t: 1000000000000
    q: 1000000000000000
    qq: 1000000000000000000
```

Reglas:

- Los sufijos son ASCII alfanuméricos y cortos.
- No puede haber duplicados ignorando mayúsculas.
- El multiplicador debe ser positivo y caber en `long`.
- Los multiplicadores posteriores a `10^18` no se soportan: no producen cantidades válidas dentro de `long`.

## Presentación Monetaria

### Formatos

| Valor | Ejemplo con `12500` | Uso |
| --- | --- | --- |
| `RAW` | `12500` | Integraciones o valores exactos. |
| `FORMATTED` | `12,500` | Lectura detallada con separadores. |
| `COMPACT` | `12.5K` | Mensajes cortos y cifras altas. |

El formato no cambia el balance ni cálculos. Solo modifica su representación.

### Configuración localizada

Cada catálogo `messages/<language>.yml` define la moneda de su idioma:

```yaml
currency:
  display-name: "Points"
  symbol: ""
  format: "%price% %display-name%"
  price-format: "COMPACT"
```

`format` debe contener `%price%` exactamente una vez. Los placeholders permitidos son:

```text
%price%
%symbol%
%display-name%
```

El patrón es texto plano, no MiniMessage. Los estilos pertenecen al mensaje o `Component` que lo contiene.

### Contratos de PlaceholderAPI

Los contratos anteriores se preservan:

```text
%progressengine_points%             valor raw
%progressengine_points_formatted%   valor con separadores
%progressengine_points_compact%     valor compacto
%progressengine_ready%              readiness
```

Se agrega:

```text
%progressengine_points_display%
```

`points_display` usa `currency.format` y el `price-format` del idioma. Ningún placeholder numérico devuelve tags MiniMessage ni códigos de color.

## Colores por Magnitud

Los colores distinguen visualmente cifras grandes sin alterar el valor ni convertir el color en significado de error o éxito.

```yaml
economy:
  amount-colors:
    enabled: true
    tiers:
      - minimum: 0
        color: "#2bd66f"
      - minimum: 1000
        color: "#a3d14d"
      - minimum: 1000000
        color: "#ebbc23"
      - minimum: 1000000000
        color: "#eb7b23"
      - minimum: 1000000000000
        color: "#ff9999"
      - minimum: 1000000000000000
        color: "#ff5353"
      - minimum: 1000000000000000000
        color: "#d92f45"
```

Se toma el tier con el `minimum` mayor que no supere el valor real.

Reglas de validación:

- El primer tier debe empezar en `0`.
- Los mínimos son estrictamente crecientes.
- Ningún mínimo supera `maximumBalance`.
- El color es hexadecimal de seis dígitos.
- Debe existir al menos un tier.

Los colores se aplican a `Component` en chat, action bar, title, boss bar, historial y Dialog. Las cantidades exactas en confirmaciones deben conservar alta legibilidad, aunque el monto principal esté coloreado.

## Confirmación de Pay

### Configuración

```yaml
pay:
  minimum: 1
  maximum: 999999999999999
  cooldown-seconds: 5
  confirmation:
    enabled: true
    threshold: 10000000
    expiry-seconds: 30
  retry-retention-seconds: 86400
```

Si `enabled` es verdadero y `amount >= threshold`, el pago crea una intención en estado `AWAITING_CONFIRMATION` y no mueve fondos todavía.

### Garantía de la intención

La intención está ligada a:

- token aleatorio e impredecible;
- emisor;
- receptor;
- cantidad exacta;
- razón;
- `OperationId`;
- revisión observada del emisor;
- expiración.

El token no sustituye la transacción MySQL. La transferencia final vuelve a validar fondos, máximo del receptor y la revisión del emisor.

### Revisión transaccional

La revisión no se valida solo contra caché. El flujo correcto es:

```text
crear intención con revisión observada
  -> usuario confirma
  -> reservar OperationId
  -> bloquear sender y receiver en MySQL
  -> comparar revisión bloqueada con revisión esperada
  -> transferir o rechazar STALE_CONFIRMATION
```

La reserva/replay idempotente se evalúa antes de la precondición de revisión. Así, un retry de un commit ya confirmado devuelve su resultado original y no aparece falsamente como obsoleto.

## Dialog de Paper

### Decisión de tecnología

El Dialog se construye con Paper directamente:

- `Dialog.create(...)`
- `DialogBase`
- `DialogType.confirmation(...)`
- `ActionButton`
- `DialogAction.customClick(...)`
- `Player.showDialog(...)`

No se usa zMenu. zMenu puede crear Dialogs, pero su implementación actual no propaga de forma correcta placeholders contextuales por apertura. Para una intención económica dinámica, Paper elimina estado global, PlaceholderAPI temporal y comandos simulados.

### Contenido obligatorio

El Dialog debe mostrar:

```text
Receptor
Monto principal, con color por magnitud
Cantidad exacta
Saldo del emisor después del pago
Expiración
Confirmar
Cancelar
```

El receptor y el monto principal deben ser visualmente dominantes. La cantidad exacta evita que la reducción compacta oculte una cifra importante.

### Interacción

- Confirmar usa callback Paper y `uses(1)`.
- El callback tiene `lifetime` igual a la expiración de la intención.
- Cancelar no toca dinero ni requiere un estado adicional: la intención expira normalmente.
- Escape equivale a cancelar y es configurable.
- El Dialog no pausa el juego salvo configuración explícita.
- Si el jugador se desconecta, no se abre el Dialog ni se transfiere dinero.
- Si el Dialog no puede abrirse, se debe ofrecer fallback seguro mediante `/points pay confirm <token>`.

## Archivo del Dialog

La UI, textos y dimensiones viven en:

```text
dialogs/pay-confirmation.yml
```

No se debe crear una DSL genérica de menús. El schema es específico para esta confirmación.

```yaml
config-version: 1

behavior:
  can-close-with-escape: true
  pause: false
  body-width: 420
  confirm-button-width: 150
  cancel-button-width: 150

locales:
  en:
    title: "Confirm payment"
    external-title: "Confirm Points payment"
    body:
      - "Review the details carefully before confirming."
      - ""
      - "Receiving player"
      - "<receiver>"
      - ""
      - "Amount to send"
      - "<amount>"
      - "Exact amount: <amount_exact>"
      - ""
      - "Your balance after payment"
      - "<balance_after>"
      - ""
      - "This confirmation expires in <expires_in> seconds."
    confirm:
      label: "Confirm payment"
      tooltip: "Send exactly <amount_exact>"
    cancel:
      label: "Cancel"
      tooltip: "No transfer will be made."

  es:
    title: "Confirmar pago"
    external-title: "Confirmar pago de Points"
    body:
      - "Revisá cuidadosamente los datos antes de confirmar."
      - ""
      - "Jugador receptor"
      - "<receiver>"
      - ""
      - "Monto a enviar"
      - "<amount>"
      - "Cantidad exacta: <amount_exact>"
      - ""
      - "Tu saldo después del pago"
      - "<balance_after>"
      - ""
      - "Esta confirmación vence en <expires_in> segundos."
    confirm:
      label: "Confirmar pago"
      tooltip: "Enviar exactamente <amount_exact>"
    cancel:
      label: "Cancelar"
      tooltip: "No se realizará ninguna transferencia."
```

Placeholders permitidos:

```text
<receiver>
<amount>
<amount_exact>
<balance_after>
<expires_in>
```

`<receiver>` y `<amount>` se insertan como `Component`, no se interpolan como MiniMessage. Los demás son texto seguro.

## Carga, Reload y Validación

`dialogs/pay-confirmation.yml` se trata igual que los demás archivos propios de ProgressEngine:

1. Se lee desde defaults o data folder.
2. Se procesa con BoostedYAML y `config-version`.
3. Se validan rutas, locales, anchos, body y placeholders.
4. Se construye `PayConfirmationDialogSettings` inmutable.
5. Se agrega al `ConfigurationSnapshot` y `LoadedConfiguration`.
6. Se persiste junto al resto de documentos de configuración.
7. Un reload inválido conserva íntegramente el snapshot anterior, incluido el Dialog anterior.

## Edge Cases

| Caso | Resultado |
| --- | --- |
| Auto-transferencia | Rechazada. |
| Target offline conocido | Permitido. |
| Target desconocido | Rechazado. |
| Token vencido | Rechazado. |
| Doble clic | Un solo uso; nunca duplica la transferencia. |
| Balance cambia con Dialog abierto | Rechazo `STALE_CONFIRMATION`. |
| Receptor supera el máximo | Rechazo `BALANCE_LIMIT_EXCEEDED`. |
| Fondos insuficientes al confirmar | Rechazo `INSUFFICIENT_FUNDS`. |
| Commit ambiguo | Retry con mismo token y `OperationId`. |
| Cerrar/Escape/Cancelar | No transfiere. |
| Fallo al abrir Dialog | Fallback textual seguro. |
| Reload inválido | Se conserva configuración anterior. |

## Criterios de Cierre

- [ ] Los atajos están validados y se aplican al resultado expandido.
- [ ] Todos los montos visibles usan el formateador central.
- [ ] Los placeholders previos conservan contratos.
- [ ] El nuevo placeholder display respeta el formato localizado.
- [ ] Los colores se aplican solo en `Component`.
- [ ] La revisión de confirmación se valida bajo lock transaccional.
- [ ] El Dialog Paper muestra toda la información obligatoria.
- [ ] El Dialog se configura desde `dialogs/pay-confirmation.yml` mediante BoostedYAML.
- [ ] El reload del Dialog es atómico y seguro.
- [ ] Las pruebas unitarias y de compilación pasan sin Testcontainers.

## Referencias

- `docs/producto/diseno-final-progressengine.md`: modelo económico, comandos, confirmación e invariantes base.
- `progressengine-paper/src/main/java/com/stephanofer/progressengine/command/CommandParsers.java`
- `progressengine-paper/src/main/java/com/stephanofer/progressengine/localization/PointsNumberFormatter.java`
- `progressengine-paper/src/main/java/com/stephanofer/progressengine/localization/PointsDisplay.java`
- `progressengine-paper/src/main/java/com/stephanofer/progressengine/account/AccountEconomy.java`
- `progressengine-paper/src/main/java/com/stephanofer/progressengine/command/PayConfirmationDialog.java`
- `progressengine-paper/src/main/java/com/stephanofer/progressengine/config/BoostedYamlConfigurationLoader.java`
