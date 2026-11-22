# Analizador WiFi+

Analizador Wi-Fi para Android (8.0 o superior). Sin anuncios, sin permiso de internet y en español.

## Funciones
- **Redes**: lista con señal, canal, ancho real, seguridad, estándar (Wi-Fi 4/5/6/6E/7), distancia estimada, búsqueda y filtros.
- **Canales**: gráfico por banda (2.4 / 5 / 6 GHz), con la zona DFS marcada y etiquetas que no se enciman.
- **Tiempo**: evolución de la señal (2, 5 o 10 min). Puedes ocultar o mostrar cada red.
- **Mejor canal**: calificación de 0 a 10 por canal y ancho (20/40/80/160 MHz), con la recomendación explicada. Excluye las redes de tu propio router.
- **Conexión**: IP, puerta de enlace, DNS, velocidades y acceso directo al panel del router.
- **Buscador de señal**: medidor grande con sonido para ubicar el router o el repetidor.
- **Exportar a CSV** (compatible con Excel).

## Mejoras respecto de las quejas habituales
| Problema reportado | Solución |
|---|---|
| Se congela 2 min por el límite de 4 escaneos de Android | Reparte los escaneos (1 cada 30 s), usa los del sistema y explica cómo quitar el límite |
| Redes "fantasma" que ya no existen | Descarta resultados más antiguos que el tiempo configurado |
| Ancho o centro de canal mal dibujado en 80/160 MHz | Corrige `centerFreq0/1` y valida datos incoherentes del driver |
| Nombres encimados en el gráfico | Colocación de etiquetas sin superposición |
| Cierres por BSSID duplicados | Elimina los duplicados antes de mostrarlos |
| Señal de tu red lenta en actualizarse | La red conectada se mide cada segundo sin gastar escaneos |
| Recomendaciones sin explicación | Muestra la interferencia en dB y por qué se elige el canal |
| Anuncios y funciones de pago | Todo es gratis y sin anuncios |
| Pide permisos sin explicar | Pantalla clara; la app no tiene acceso a internet |

## Compilar
- **GitHub Actions**: sube el proyecto (o este ZIP) a un repositorio con `.github/workflows/build.yml`. El APK aparece en *Actions → Artifacts*.
- **Android Studio**: abre la carpeta y usa *Build → Build APK(s)*.
