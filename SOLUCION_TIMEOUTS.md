# Solución a los timeouts de la base de datos

## Problema resuelto
Se han arreglado los timeouts de conexión del pool de la base de datos que causaban:
- Mensajes de error: "Timeout obteniendo conexión del pool después de 10 segundos"
- Bloqueos del servidor (watchdog thread errors)
- Comandos del plugin que no respondían

## Cambios realizados

### 1. Arreglados Connection Leaks (CRÍTICO)
Se corrigieron 8+ métodos en `DatabaseManager.java` que no estaban devolviendo conexiones al pool correctamente:
- `setupDatabase()` 
- `playerExists()`
- `registerPlayer()` y `registerPlayerAsync()`
- `setTrophies()` y `setTrophiesAsync()`
- `getTrophies()`
- `setKillStreak()` y `getKillStreak()`
- `processBatchUpdates()`

**Antes (problemático):**
```java
Connection conn = null;
try {
    conn = getConnection();
    // ... operaciones ...
} finally {
    returnConnection(conn); // Podía fallar
}
```

**Después (correcto):**
```java
try (Connection conn = getConnection()) {
    // ... operaciones ...
} // Se cierra automáticamente
```

### 2. Configuración del Pool Optimizada
- **pool_size**: Aumentado de 1 a 3 conexiones para mejor concurrencia
- **Timeouts coordinados**: Pool timeout (5s) y SQLite busy_timeout (5s)
- **Health check**: Verificación periódica cada 2 minutos

### 3. Operaciones Asíncronas
- `checkStreakUnlocks()` ahora se ejecuta asíncronamente para no bloquear el hilo principal del servidor

### 4. Mejoras de Monitoreo
- Estadísticas del pool en logs cuando hay timeouts
- Health check automático para limpiar conexiones inválidas
- Método `getPoolStats()` para debugging

## Cómo verificar que está solucionado

### 1. Logs del servidor
**Deberías VER al iniciar el plugin:**
```
[INFO]: [Kenicompetitivo] Connection pool inicializado con 3 conexiones
[INFO]: [Kenicompetitivo] Base de datos inicializada con índices optimizados
```

**NO deberías ver más:**
```
[WARN]: [Kenicompetitivo] Timeout obteniendo conexión del pool después de 10 segundos
[ERROR]: --- DO NOT REPORT THIS TO PURPUR - THIS IS NOT A BUG OR A CRASH
```

### 2. Pruebas a realizar
1. **Usar comandos del plugin frecuentemente:**
   - `/kenicompetitivo racha add <jugador> 100`
   - `/kenicompetitivo panel`
   - `/cosmeticos`

2. **Monitorear durante uso normal:**
   - Los comandos deben responder inmediatamente
   - No debe haber pausas/freezes del servidor
   - Los cosméticos deben desbloquearse sin problemas

3. **Verificar en momentos de alta actividad:**
   - Múltiples jugadores usando comandos simultáneamente
   - Durante eventos PvP con muchas muertes

### 3. Comando de debug (opcional)
Si quieres verificar el estado del pool, puedes agregar este comando temporal:
```java
// En KenicompetitivoCommand.java, agregar:
if (args[0].equals("poolstats")) {
    sender.sendMessage("Pool stats: " + plugin.getDatabaseManager().getPoolStats());
    return true;
}
```

## Configuración recomendada

### config.yml
```yaml
database:
  pool_size: 3  # Óptimo para SQLite con concurrencia
  debug: false  # Activar solo si necesitas debugging
  batch_process_interval: 100
  batch_size_threshold: 15
```

### Monitoreo
- Los health checks se ejecutan automáticamente cada 2 minutos
- Si ves "Health check: Removed X invalid connections" ocasionalmente, es normal
- Si aparece constantemente, puede indicar otro problema

## En caso de problemas persistentes

Si aún experimentas timeouts después de estos cambios:

1. **Activar debug temporal:**
   ```yaml
   database:
     debug: true
   ```

2. **Verificar plugins conflictivos:**
   - Otros plugins que usen SQLite intensivamente
   - Plugins que bloqueen el hilo principal

3. **Revisar recursos del servidor:**
   - CPU/RAM disponible
   - Velocidad del disco (SSD recomendado)

4. **Aumentar pool_size si es necesario:**
   ```yaml
   database:
     pool_size: 5  # Solo si persisten problemas
   ```

## Compatibilidad
- ✅ Minecraft 1.20.4 (Purpur)
- ✅ SQLite (modo WAL)
- ✅ Bukkit/Spigot/Paper/Purpur
- ✅ Plugins existentes (Vault, PlaceholderAPI, etc.)

---
**Fecha de corrección:** Diciembre 2024  
**Desarrollador:** Copilot AI Assistant  
**Testing requerido:** Sí - monitorear en servidor de producción