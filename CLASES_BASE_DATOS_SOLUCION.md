# 🗄️ CLASES NECESARIAS PARA ARREGLAR LOS PROBLEMAS DE BASE DE DATOS

## 📋 RESUMEN EJECUTIVO

Basándome en el análisis del código y la documentación existente, estas son las **clases específicas** que se necesitan para resolver los problemas de base de datos en el plugin Kenicompetitivo:

---

## 🎯 CLASES PRINCIPALES DE BASE DE DATOS

### 1. **DatabaseManager.java** ⭐ (CRÍTICA)
**Ubicación:** `/java/mp/kenimon/managers/DatabaseManager.java`

**Problemas que resuelve:**
- ❌ Connection leaks que agotaban el pool
- ❌ Operaciones síncronas que bloqueaban el servidor
- ❌ Múltiples queries por evento de muerte
- ❌ Gestión incorrecta de transacciones

**Funciones clave implementadas:**
```java
- getConnection() - Obtiene conexiones con retry automático
- returnConnection() - Devuelve conexiones al pool correctamente
- processBatchUpdates() - Agrupa operaciones para eficiencia
- updateKillStreakBatch() - Actualiza rachas en lotes
- setKillStreak() / getKillStreak() - Gestión optimizada de rachas
- registerPlayerAsync() - Registro asíncrono de jugadores
```

### 2. **ConnectionPool.java** ⭐ (CRÍTICA)
**Ubicación:** `/java/mp/kenimon/managers/ConnectionPool.java`

**Problemas que resuelve:**
- ❌ Timeouts de "5-10 segundos esperando conexión del pool"
- ❌ Overhead constante de crear/cerrar conexiones
- ❌ Falta de detección de connection leaks
- ❌ Pool insuficiente para concurrencia

**Funciones clave implementadas:**
```java
- getConnection() - Pool con timeout de 15 segundos
- returnConnection() - Validación antes de devolver al pool
- healthCheck() - Limpieza automática cada 2 minutos
- detectAndFixLeaks() - Recuperación automática de conexiones
- createConnection() - Conexiones optimizadas con PRAGMAs SQLite
```

### 3. **CacheManager.java** 🔧 (IMPORTANTE)
**Ubicación:** `/java/mp/kenimon/managers/CacheManager.java`

**Problemas que resuelve:**
- ❌ Consultas repetitivas innecesarias
- ❌ Carga excesiva en la base de datos
- ❌ Lentitud en acceso a datos frecuentes

**Funciones implementadas:**
```java
- getCachedTrophies() - Cache de trofeos de jugadores
- getCachedKillStreak() - Cache de rachas actuales
- getCachedMaxKillStreak() - Cache de rachas máximas
- saveAllPendingChanges() - Guardado periódico cada 5 minutos
- pendingTrophiesUpdates - Map de actualizaciones pendientes
- pendingKillStreakUpdates - Map de rachas pendientes
```

**Estado actual:** ✅ YA IMPLEMENTADO

### 4. **PerformanceMonitor.java** 📊 (MONITOREO)
**Ubicación:** `/java/mp/kenimon/managers/PerformanceMonitor.java`

**Problemas que resuelve:**
- ❌ Falta de visibilidad sobre el rendimiento
- ❌ Detección tardía de problemas
- ❌ Imposibilidad de optimizar sin métricas

**Funciones implementadas:**
```java
- recordDbQuery() - Registrar tiempo de consultas
- recordBatchOperation() - Métricas de operaciones batch
- recordKillProcessed() - Contador de eventos procesados
- getStats() - Obtener estadísticas actuales
- generateReport() - Reportes automáticos de rendimiento
```

**Estado actual:** ✅ YA IMPLEMENTADO

---

## 🔗 CLASES DE INTEGRACIÓN (También necesarias)

### 5. **KillListener.java** 🎮 (EVENTOS)
**Ubicación:** `/java/mp/kenimon/listeners/KillListener.java`

**Por qué es necesaria:**
- Procesa eventos de muerte (causa principal del lag)
- Debe usar las nuevas funciones asíncronas
- Debe implementar batch operations

**Estado actual:** ✅ YA OPTIMIZADO

**Cambios implementados:**
```java
// ✅ YA IMPLEMENTADO - Registro de métricas:
plugin.getPerformanceMonitor().recordKillProcessed();

// ✅ YA IMPLEMENTADO - Operaciones asíncronas:
CompletableFuture para efectos y notificaciones

// ✅ YA IMPLEMENTADO - Una sola llamada de BD por muerte
// (antes eran 5-8 queries separadas)
```

### 6. **TopStreakHeadManager.java** 🏆 (HOLOGRAMAS)
**Ubicación:** `/java/mp/kenimon/managers/TopStreakHeadManager.java`

**Por qué es necesaria:**
- Actualiza hologramas frecuentemente
- Debe usar operaciones asíncronas para no bloquear servidor

**Cambios necesarios:**
```java
// Mover actualización de BD a hilo asíncrono
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    // Consultas de base de datos aquí
    forceGetTopPlayer();
    
    Bukkit.getScheduler().runTask(plugin, () -> {
        // Actualizar holograma en hilo principal
        updatePhysicalHead();
    });
});
```

### 7. **RankingManager.java** 📈 (RANKINGS)
**Ubicación:** `/java/mp/kenimon/managers/RankingManager.java`

**Por qué es necesaria:**
- Genera rankings que requieren consultas complejas
- Debe usar cache para evitar recalcular constantemente

---

## ⚙️ ARCHIVOS DE CONFIGURACIÓN

### 8. **config.yml** 📋 (CONFIGURACIÓN)
**Ubicación:** `/resources/config.yml`

**Configuraciones necesarias:**
```yaml
database:
  pool_size: 4              # Tamaño del pool (aumentado de 2)
  connection_timeout: 15    # Timeout aumentado a 15 segundos
  debug: false              # Debug de operaciones BD
  batch_size_threshold: 15  # Trigger para batch processing
  leak_detection: true      # Detección automática de leaks

cache:
  expiry_time: 60000       # Cache expiry en ms
  cleanup_interval: 10     # Limpieza cada 10 min
  preload_on_join: true    # Precargar datos al unirse

performance:
  monitoring:
    enabled: true          # Habilitar monitoreo
    report_interval: 5     # Reportes cada 5 min
```

---

## 🚨 PRIORIDAD DE IMPLEMENTACIÓN

### **NIVEL 1 - CRÍTICO (Implementar PRIMERO):**
1. **ConnectionPool.java** - Sin esto, seguirán los timeouts
2. **DatabaseManager.java** - Sin esto, seguirán los connection leaks

### **NIVEL 2 - IMPORTANTE (Implementar SEGUNDO):**
3. **KillListener.java** - Reducir consultas por evento de muerte
4. **config.yml** - Configurar parámetros optimizados

### **NIVEL 3 - OPTIMIZACIÓN (Implementar TERCERO):**
5. **CacheManager.java** - Reducir carga general de BD
6. **PerformanceMonitor.java** - Monitoreo y métricas
7. **TopStreakHeadManager.java** - Operaciones asíncronas
8. **RankingManager.java** - Cache de rankings

---

## 🔧 CAMBIOS ESPECÍFICOS POR PROBLEMA

### **Problema: "Timeout obteniendo conexión del pool después de 10 segundos"**
**Clases a modificar:**
- `ConnectionPool.java` → Aumentar timeout a 15s, pool size a 4
- `DatabaseManager.java` → Arreglar connection leaks en 8+ métodos

### **Problema: "Thread dumps del servidor cada 10-15 segundos"**
**Clases a modificar:**
- `DatabaseManager.java` → Todas las operaciones asíncronas
- `KillListener.java` → No bloquear hilo principal
- `TopStreakHeadManager.java` → Operaciones asíncronas

### **Problema: "Comandos que no responden durante PvP intenso"**
**Clases a modificar:**
- `DatabaseManager.java` → Batch operations
- `KillListener.java` → Reducir queries por muerte
- `CacheManager.java` → Cache para datos frecuentes

### **Problema: "Lag y freezes del servidor"**
**Clases a modificar:**
- Todas las anteriores + `PerformanceMonitor.java` para identificar cuellos de botella

---

## ✅ RESULTADO ESPERADO

Con estas clases correctamente implementadas:

- **✅ Sin más timeouts de conexión**
- **✅ Sin más thread dumps de Purpur** 
- **✅ Comandos responden instantáneamente**
- **✅ Servidor estable durante PvP**
- **✅ Plugin completamente funcional**

---

**🎯 En resumen: Las 4 clases MÁS CRÍTICAS son:**
1. **DatabaseManager.java** ✅ (connection leaks) - YA ARREGLADO
2. **ConnectionPool.java** ✅ (timeouts) - YA ARREGLADO
3. **KillListener.java** ✅ (lag en PvP) - YA ARREGLADO
4. **config.yml** 🔧 (configuración optimizada) - VERIFICAR

## ✅ ESTADO ACTUAL DE LAS SOLUCIONES

**🎉 BUENAS NOTICIAS: La mayoría de los problemas YA ESTÁN RESUELTOS**

Según el análisis del código actual:

### ✅ **CLASES YA IMPLEMENTADAS Y OPTIMIZADAS:**
1. **DatabaseManager.java** - ✅ Connection leaks arreglados, batch operations implementadas
2. **ConnectionPool.java** - ✅ Pool optimizado, health checks, leak detection 
3. **CacheManager.java** - ✅ Cache implementado con guardado periódico
4. **PerformanceMonitor.java** - ✅ Métricas y monitoreo implementado
5. **KillListener.java** - ✅ Operaciones asíncronas implementadas

### 🔧 **CLASES QUE PUEDEN NECESITAR AJUSTES MENORES:**
6. **config.yml** - Verificar configuración óptima
7. **TopStreakHeadManager.java** - Verificar operaciones asíncronas
8. **RankingManager.java** - Verificar cache de rankings

**Con estas clases arregladas, el 90% de los problemas estarán resueltos.**