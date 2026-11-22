import os
import sys
import base64
from playwright.sync_api import sync_playwright

html_content = """<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>Informe Técnico - Microservicios Distribuidos UPSE</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Libre+Baskerville:ital,wght@0,400;0,700;1,400&family=JetBrains+Mono:wght@400;600&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
<style>
  @page {
    size: A4;
    margin: 22mm 20mm 22mm 20mm;
  }
  
  * {
    box-sizing: border-box;
  }

  body {
    font-family: 'Libre Baskerville', Georgia, serif;
    font-size: 10.5pt;
    line-height: 1.55;
    color: #111111;
    margin: 0;
    padding: 0;
    text-rendering: optimizeLegibility;
  }

  /* Cover Page */
  .cover-page {
    page-break-after: always;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    min-height: 240mm;
    text-align: center;
    padding: 4mm 5mm 6mm 5mm;
  }

  .cover-header {
    margin-top: 2mm;
  }

  .cover-logo {
    margin-bottom: 3mm;
    font-family: 'Inter', sans-serif;
    font-size: 26pt;
    font-weight: 800;
    letter-spacing: 2px;
    color: #111111;
  }

  .cover-logo img {
    height: 32mm;
    max-width: 100%;
    object-fit: contain;
  }

  .cover-inst {
    font-size: 13pt;
    font-weight: 700;
    letter-spacing: 0.5px;
    color: #111111;
    margin: 2mm 0;
  }

  .cover-fac {
    font-size: 11pt;
    font-weight: 600;
    color: #333333;
    margin: 1mm 0;
  }

  .cover-rule {
    border: none;
    border-top: 1.2pt solid #111111;
    margin: 5mm 0;
  }

  .cover-subject {
    font-family: 'Inter', sans-serif;
    font-size: 11.5pt;
    font-weight: 700;
    letter-spacing: 1.5px;
    margin: 3mm 0;
  }

  .cover-title-box {
    margin: 12mm 0 8mm 0;
  }

  .cover-title {
    font-size: 18pt;
    font-weight: 700;
    line-height: 1.35;
    color: #111111;
    margin-bottom: 4mm;
  }

  .cover-subtitle {
    font-size: 11.5pt;
    font-style: italic;
    color: #334155;
    line-height: 1.45;
  }

  .cover-meta {
    margin-top: auto;
    text-align: left;
    background: #F8FAFC;
    border: 0.8pt solid #CBD5E1;
    border-left: 4pt solid #111111;
    border-radius: 4px;
    padding: 6mm 8mm;
    font-family: 'Inter', sans-serif;
    font-size: 9.5pt;
  }

  .meta-row {
    display: flex;
    margin-bottom: 2.5mm;
  }

  .meta-label {
    width: 45mm;
    font-weight: 700;
    color: #1E293B;
  }

  .meta-value {
    flex: 1;
    color: #0F172A;
  }

  .meta-value a {
    color: #0F172A;
    text-decoration: underline;
  }

  /* Body Content */
  .content-body {
    padding-top: 2mm;
  }

  h1 {
    font-family: 'Inter', sans-serif;
    font-size: 13.5pt;
    font-weight: 700;
    color: #111111;
    margin: 7mm 0 2.5mm 0;
    padding-bottom: 1.5mm;
    border-bottom: 1.2pt solid #111111;
    page-break-after: avoid;
  }

  h2 {
    font-family: 'Inter', sans-serif;
    font-size: 11pt;
    font-weight: 700;
    color: #1E293B;
    margin: 4.5mm 0 2mm 0;
    page-break-after: avoid;
  }

  p {
    margin: 0 0 3mm 0;
    text-align: justify;
  }

  ul, ol {
    margin: 0 0 3mm 0;
    padding-left: 5mm;
  }

  li {
    margin-bottom: 1.2mm;
    text-align: justify;
  }

  /* Tables */
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 3.5mm 0 1.5mm 0;
    font-family: 'Inter', sans-serif;
    font-size: 8pt;
    line-height: 1.35;
    page-break-inside: avoid;
  }

  th {
    background-color: #1E293B;
    color: #FFFFFF;
    font-weight: 600;
    text-align: left;
    padding: 2.5mm 2mm;
    border: none;
  }

  td {
    padding: 2mm 2mm;
    border-bottom: 0.6pt solid #E2E8F0;
    vertical-align: top;
  }

  tr:nth-child(even) td {
    background-color: #F8FAFC;
  }

  .table-caption {
    font-family: 'Inter', sans-serif;
    font-size: 8pt;
    font-weight: 600;
    color: #475569;
    margin-bottom: 3.5mm;
    text-align: center;
    page-break-before: avoid;
  }

  /* Formal Box */
  .formal-box {
    background-color: #F8FAFC;
    border: 0.6pt solid #CBD5E1;
    border-left: 3.5pt solid #111111;
    border-radius: 4px;
    padding: 3.5mm 4.5mm;
    margin: 3.5mm 0;
    font-size: 9pt;
    line-height: 1.5;
    page-break-inside: avoid;
  }

  .formal-box strong {
    color: #0F172A;
  }

  /* Code & Monospace */
  code, pre {
    font-family: 'JetBrains Mono', monospace;
    font-size: 8.5pt;
  }

  code {
    background-color: #F1F5F9;
    padding: 1pt 3pt;
    border-radius: 2px;
    border: 0.4pt solid #E2E8F0;
    color: #0F172A;
  }

  pre {
    background-color: #0F172A;
    color: #F8FAFC;
    padding: 4mm 5mm;
    border-radius: 4px;
    overflow-x: hidden;
    margin: 3mm 0;
    line-height: 1.45;
  }

  .badge-pass {
    display: inline-block;
    background-color: #DCFCE7;
    color: #166534;
    font-weight: 700;
    font-size: 7.5pt;
    padding: 1pt 5pt;
    border-radius: 3px;
    border: 0.5pt solid #86EFAC;
  }
</style>
</head>
<body>

<!-- PÁGINA 1: PORTADA -->
<div class="cover-page">
  <div class="cover-header">
    <div class="cover-logo">__UPSE_LOGO_TAG__</div>
    <div class="cover-inst">UNIVERSIDAD ESTATAL PENÍNSULA DE SANTA ELENA</div>
    <div class="cover-fac">Facultad de Sistemas y Telecomunicaciones</div>
    <div class="cover-fac">Carrera de Ingeniería de Software</div>
    
    <hr class="cover-rule">
    <div class="cover-subject">APLICACIONES DISTRIBUIDAS</div>
    <hr class="cover-rule">
  </div>

  <div class="cover-title-box">
    <div class="cover-title">Informe Técnico: Microservicios con Descubrimiento Dinámico, Balanceo de Carga y Persistencia Replicada</div>
    <div class="cover-subtitle">Diseño, Implementación y Validación Experimental de Alta Disponibilidad, Tolerancia a Fallos y Segregación de Persistencia</div>
  </div>

  <div class="cover-meta">
    <div class="meta-row">
      <div class="meta-label">Integrantes:</div>
      <div class="meta-value">
        <b>Damián Jesús Torres Cadena</b><br>
        <b>Melanie Adriana Tomalá Merejildo</b><br>
        <b>Jean Pierre Cedeño Andrade</b>
      </div>
    </div>
    <div class="meta-row">
      <div class="meta-label">Asignatura:</div>
      <div class="meta-value">Aplicaciones Distribuidas</div>
    </div>
    <div class="meta-row">
      <div class="meta-label">Carrera / Período:</div>
      <div class="meta-value">Ingeniería de Software — Período Académico 2026-2</div>
    </div>
    <div class="meta-row">
      <div class="meta-label">Repositorio GitHub:</div>
      <div class="meta-value"><a href="https://github.com/DevTorres404/microservicios">https://github.com/DevTorres404/microservicios</a></div>
    </div>
    <div class="meta-row">
      <div class="meta-label">Video Demostrativo:</div>
      <div class="meta-value"><a href="https://youtu.be/Gyf0GFP-oaI">https://youtu.be/Gyf0GFP-oaI</a></div>
    </div>
    <div class="meta-row" style="margin-bottom:0;">
      <div class="meta-label">Fecha de Entrega:</div>
      <div class="meta-value">Noviembre de 2026</div>
    </div>
  </div>
</div>

<!-- CUERPO DEL INFORME -->
<div class="content-body">

  <h1>1. Arquitectura General y Topología del Sistema</h1>
  <p>
    El sistema implementa una arquitectura orientada a microservicios sobre <b>Java 21 LTS</b>, <b>Spring Boot 3.5.16</b> y <b>Spring Cloud 2025.0.3</b>, integrando persistencia relacional distribuida en <b>PostgreSQL 16</b> mediante replicación física asíncrona por streaming de WAL.
  </p>
  <p>
    El entorno se encuentra orquestado de manera reproducible mediante un clúster de <b>9 contenedores</b> coordinados en Docker Compose bajo una red privada común (<code>backend</code>), asegurando mínima exposición perimetral hacia el host:
  </p>

  <table>
    <thead>
      <tr>
        <th style="width: 20%;">Componente</th>
        <th style="width: 25%;">Contenedor(es)</th>
        <th style="width: 17%;">Puertos</th>
        <th style="width: 38%;">Responsabilidad Arquitectónica</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><b>API Gateway</b></td>
        <td><code>gateway</code></td>
        <td><code>8080:8080</code></td>
        <td>Punto de entrada único. Enrutamiento declarativo <code>lb://</code>, inyección de <code>X-Correlation-Id</code>, circuit breakers y métricas.</td>
      </tr>
      <tr>
        <td><b>Service Registry</b></td>
        <td><code>eureka-server</code></td>
        <td><code>8761:8761</code></td>
        <td>Catálogo de descubrimiento standalone. Gestión de leases (5 s renovación / 15 s expiración) y autopreservación desactivada.</td>
      </tr>
      <tr>
        <td><b>Servicio Pedidos</b></td>
        <td><code>pedidos-1</code><br><code>pedidos-2</code></td>
        <td>Interno <code>8082</code></td>
        <td>Gestión del ciclo de vida de órdenes, máquina de estados, cliente Feign hacia inventario y orquestación transaccional local.</td>
      </tr>
      <tr>
        <td><b>Servicio Inventario</b></td>
        <td><code>inventario-1</code><br><code>inventario-2</code></td>
        <td>Interno <code>8081</code></td>
        <td>Control de existencias, reservas atómicas condicionales, reposición y liberaciones compensatorias idempotentes.</td>
      </tr>
      <tr>
        <td><b>Clúster PostgreSQL</b></td>
        <td><code>pg-primary</code><br><code>pg-replica-1</code><br><code>pg-replica-2</code></td>
        <td>Interno <code>5432</code></td>
        <td>Persistencia segregada: 1 nodo primario de lectura/escritura y 2 réplicas físicas <i>hot standby</i> por streaming WAL.</td>
      </tr>
    </tbody>
  </table>
  <div class="table-caption">Tabla 1: Matriz de componentes, puertos y asignación de responsabilidades.</div>

  <h2>1.1 Distinción de los Tres Mecanismos Independientes de Enrutamiento</h2>
  <p>
    Uno de los principales aportes del diseño radica en no confundir los niveles de resolución. El sistema desacopla formalmente tres mecanismos:
  </p>

  <table>
    <thead>
      <tr>
        <th style="width: 28%;">Mecanismo</th>
        <th style="width: 22%;">Nivel</th>
        <th style="width: 25%;">Tecnología</th>
        <th style="width: 25%;">Criterio de Resolución</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><b>Descubrimiento de Procesos</b></td>
        <td>Catálogo / Salud</td>
        <td>Netflix Eureka</td>
        <td>Registro dinámico por nombre lógico; desalojo ante pérdida de latidos (&gt; 15 s).</td>
      </tr>
      <tr>
        <td><b>Balanceo de Solicitudes HTTP</b></td>
        <td>Red de Aplicación</td>
        <td>Spring Cloud LoadBalancer</td>
        <td>Distribución round-robin entre instancias saludables reportadas por Eureka.</td>
      </tr>
      <tr>
        <td><b>Enrutamiento de Base de Datos</b></td>
        <td>Persistencia / JDBC</td>
        <td><code>AbstractRoutingDataSource</code></td>
        <td>Segregación transaccional: escrituras al primario y consultas en réplicas.</td>
      </tr>
    </tbody>
  </table>
  <div class="table-caption">Tabla 2: Comparativa de los tres mecanismos de resolución independientes.</div>

  <div class="formal-box">
    <strong>Principio de Diseño:</strong> Eureka <b>no</b> administra las réplicas de PostgreSQL, y la anotación <code>@Transactional(readOnly = true)</code> <b>no</b> balancea consultas por sí sola. La selección de instancia HTTP y la selección de conexión de base de datos son decisiones desacopladas gobernadas por capas tecnológicas independientes.
  </div>

  <h1>2. Diseño de Microservicios, Contratos y Resiliencia</h1>
  
  <h2>2.1 Aislamiento de Dominios y Persistencia Privada</h2>
  <p>
    Los microservicios aplican el patrón <i>Database-per-Service</i>. Cada servicio posee un usuario y base de datos propia en el servidor PostgreSQL (<code>pedidos_db</code> e <code>inventario_db</code>). El script de auditoría automatizado (<code>scripts/check-boundaries.sh</code>) verifica que:
  </p>
  <ul>
    <li>No existan referencias cruzadas entre esquemas SQL de pedidos e inventario.</li>
    <li>No existan dependencias o importaciones directas de clases de dominio entre servicios.</li>
    <li>No existan direcciones IP o puertos rígidos configurados en el código.</li>
  </ul>

  <h2>2.2 Comunicación Inter-Servicios con Resiliencia</h2>
  <p>
    La creación de pedidos requiere consultar y reservar stock en inventario. Esta interacción se resuelve mediante un cliente declarativo OpenFeign (<code>@FeignClient(name = "servicio-inventario")</code>) acoplado a Spring Cloud LoadBalancer. La resiliencia se garantiza mediante:
  </p>
  <ul>
    <li><b>Timeouts estrictos:</b> 500 ms de conexión y 1500 ms de lectura, evitando retención indefinida de hilos.</li>
    <li><b>Retryer acotado:</b> Máximo 1 reintento exclusivo ante fallos de conectividad física, ignorando errores de lógica de negocio.</li>
    <li><b>Circuit Breaker (Resilience4j):</b> Ventana de 10 llamadas, umbral de fallo del 50 % y apertura de 10 s. En estado abierto, rechaza llamadas sin tocar la red y deriva al fallback.</li>
    <li><b>Mapeo de errores RFC 9457 (<code>ProblemDetail</code>):</b> Las excepciones de caída se traducen uniformemente a <b>HTTP 503 Service Unavailable</b> con código <code>INVENTARIO_NO_DISPONIBLE</code>.</li>
  </ul>

  <table>
    <thead>
      <tr>
        <th style="width: 14%;">Método</th>
        <th style="width: 38%;">Ruta en Gateway (:8080)</th>
        <th style="width: 24%;">Servicio Destino</th>
        <th style="width: 24%;">Descripción</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><code>GET</code></td>
        <td><code>/api/inventario</code></td>
        <td><code>servicio-inventario</code></td>
        <td>Consulta general de catálogo y existencias.</td>
      </tr>
      <tr>
        <td><code>GET</code></td>
        <td><code>/api/inventario/{sku}</code></td>
        <td><code>servicio-inventario</code></td>
        <td>Consulta de stock y precio vigente por SKU.</td>
      </tr>
      <tr>
        <td><code>POST</code></td>
        <td><code>/api/inventario/reservas</code></td>
        <td><code>servicio-inventario</code></td>
        <td>Reserva atómica condicional multi-línea.</td>
      </tr>
      <tr>
        <td><code>POST</code></td>
        <td><code>/api/inventario/reservas/...</code></td>
        <td><code>servicio-inventario</code></td>
        <td>Liberación compensatoria idempotente.</td>
      </tr>
      <tr>
        <td><code>PUT</code></td>
        <td><code>/api/inventario/{sku}</code></td>
        <td><code>servicio-inventario</code></td>
        <td>Reposición y ajuste de stock en primario.</td>
      </tr>
      <tr>
        <td><code>POST</code></td>
        <td><code>/api/pedidos</code></td>
        <td><code>servicio-pedidos</code></td>
        <td>Creación de pedido orquestada en 2 fases.</td>
      </tr>
      <tr>
        <td><code>GET</code></td>
        <td><code>/api/pedidos/{id}</code></td>
        <td><code>servicio-pedidos</code></td>
        <td>Lectura crítica (<code>@CriticalRead</code>) en primario.</td>
      </tr>
      <tr>
        <td><code>GET</code></td>
        <td><code>/api/pedidos</code></td>
        <td><code>servicio-pedidos</code></td>
        <td>Listado ordinario de pedidos en réplicas.</td>
      </tr>
      <tr>
        <td><code>POST</code></td>
        <td><code>/api/pedidos/{id}/cancelar</code></td>
        <td><code>servicio-pedidos</code></td>
        <td>Cancelación y compensación best-effort.</td>
      </tr>
    </tbody>
  </table>
  <div class="table-caption">Tabla 3: Catálogo de operaciones REST expuestas a través del API Gateway.</div>

  <div class="formal-box">
    <strong>Orquestación Transaccional sin 2PC:</strong> La creación del pedido se ejecuta en dos transacciones locales: 
    (1) Tx1 registra el pedido en estado <code>PENDIENTE</code>; 
    (2) Se invoca a inventario vía Feign fuera de transacción para no bloquear filas en PostgreSQL; 
    (3) Tx2 actualiza el pedido a <code>CONFIRMADO</code> con los precios reales o a <code>RECHAZADO</code> si falta stock, aplicando liberación compensatoria ante errores de red.
  </div>

  <h1>3. Persistencia Replicada y Enrutamiento Dinámico</h1>
  <p>
    La gestión de base de datos se encapsuló en la biblioteca técnica compartida <code>libs/persistence-routing</code>, integrando los siguientes patrones:
  </p>

  <h2>3.1 Componentes del Enrutamiento Dinámico</h2>
  <ol>
    <li><b><code>RoutingDataSource</code>:</b> Extiende <code>AbstractRoutingDataSource</code> resolviendo dinámicamente la clave <code>PRIMARY</code>, <code>REPLICA_1</code> o <code>REPLICA_2</code>.</li>
    <li><b><code>LazyConnectionDataSourceProxy</code>:</b> Retarda la solicitud física de una conexión del pool de Hikari hasta la primera ejecución de una sentencia SQL real, permitiendo que el aspecto AOP determine la fuente correcta sin incurrir en cambios de conexión a mitad de transacción.</li>
    <li><b><code>ReplicaSelector</code>:</b> Implementa balanceo determinista round-robin sobre las réplicas disponibles usando un <code>AtomicInteger</code> combinado con <code>Math.floorMod</code> para prevenir desbordes numéricos.</li>
    <li><b><code>ReplicaHealthRegistry</code>:</b> Mantiene un hilo de monitoreo en segundo plano ejecutando cada 5 s la consulta <code>SELECT 1</code> y verificando <code>pg_stat_wal_receiver.status = 'streaming'</code>. La recuperación sigue una máquina de estados de dos fases: <code>DOWN &rarr; PROBING &rarr; UP</code> (requiriendo dos sondeos exitosos consecutivos).</li>
  </ol>

  <h2>3.2 Garantía Read-Your-Writes mediante <code>@CriticalRead</code></h2>
  <p>
    En un entorno con replicación asíncrona existe una ventana de retraso (<i>replication lag</i>) en la que las réplicas no reflejan de inmediato el registro recién escrito. Para evitar que un usuario cree un pedido y al recargar la pantalla reciba un 404, se diseñó la anotación <code>@CriticalRead</code>.
  </p>
  <p>
    El aspecto AOP (<code>ReadWriteRoutingAspect</code>) evalúa la precedencia estricta:
  </p>
  <div style="text-align:center; font-family:'Inter', sans-serif; font-size:9pt; font-weight:700; margin: 3mm 0; color:#1E293B;">
    @CriticalRead (PRIMARY) &nbsp;&gt;&nbsp; @Transactional(readOnly = false) (PRIMARY) &nbsp;&gt;&nbsp; @Transactional(readOnly = true) (REPLICA)
  </div>

  <h2>3.3 Modo Degradado Automático</h2>
  <p>
    Si ambas réplicas quedan fuera de servicio, el selector conmuta automáticamente todas las consultas al nodo <code>PRIMARY</code>, registrando un <code>WARN</code> único de transición e incrementando la métrica de Micrometer <code>db_routing_degraded_total</code>, preservando la disponibilidad absoluta del servicio.
  </p>

  <div class="formal-box">
    <strong>Reglas Operativas del Enrutamiento Transaccional:</strong>
    <ul style="margin: 2mm 0 0 0; padding-left: 5mm;">
      <li><b>Escrituras (<code>POST</code>, <code>PUT</code>):</b> <code>@Transactional</code> &rarr; adscripción física obligatoria a <code>pg-primary</code>.</li>
      <li><b>Lecturas Ordinarias (<code>GET /api/inventario</code>):</b> <code>@Transactional(readOnly = true)</code> &rarr; Hikari pool hacia <code>pg-replica-1</code> o <code>pg-replica-2</code>.</li>
      <li><b>Lectura Crítica (<code>GET /api/pedidos/{id}</code>):</b> <code>@CriticalRead</code> &rarr; fuerza <code>pg-primary</code> garantizando consistencia inmediata.</li>
    </ul>
  </div>

  <h1>4. Resultados y Validación Experimental (P01 a P08 y P06b)</h1>
  <p>
    Cada uno de los escenarios requeridos por la guía de la actividad fue inducido, medido y documentado mediante scripts reproducibles con captura de datos crudos (<code>docs/informes/raw/</code>):
  </p>

  <table>
    <thead>
      <tr>
        <th style="width: 10%;">Prueba</th>
        <th style="width: 25%;">Escenario</th>
        <th style="width: 29%;">Condición / Método</th>
        <th style="width: 24%;">Métrica Medida</th>
        <th style="width: 12%;">Estado</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><b>P01</b></td>
        <td>Descubrimiento Eureka</td>
        <td>Inspección de catálogo crudo</td>
        <td>4 instancias registradas UP</td>
        <td><span class="badge-pass">PASS 100%</span></td>
      </tr>
      <tr>
        <td><b>P02</b></td>
        <td>Balanceo HTTP</td>
        <td>100 peticiones secuenciales</td>
        <td>Reparto 50% / 50% (máx &le; 75%)</td>
        <td><span class="badge-pass">PASS 100%</span></td>
      </tr>
      <tr>
        <td><b>P03</b></td>
        <td>Fallo de instancia</td>
        <td><code>docker stop pedidos-2</code></td>
        <td>Expulsión: 17 s / Retorno: 18 s</td>
        <td><span class="badge-pass">PASS &ge; 90%</span></td>
      </tr>
      <tr>
        <td><b>P04</b></td>
        <td>Inter-servicios y Fallo</td>
        <td>Pedidos &rarr; Inventario (caído)</td>
        <td>Mismo <code>X-Correlation-Id</code> / 503</td>
        <td><span class="badge-pass">PASS</span></td>
      </tr>
      <tr>
        <td><b>P05</b></td>
        <td>Enrutamiento SQL</td>
        <td>Auditoría <code>log_statement=all</code></td>
        <td>0 escrituras en réplicas</td>
        <td><span class="badge-pass">PASS</span></td>
      </tr>
      <tr>
        <td><b>P06</b></td>
        <td>Fallo de réplica</td>
        <td><code>docker stop pg-replica-1</code></td>
        <td>Detección y desvío en 6 s (&le; 10 s)</td>
        <td><span class="badge-pass">PASS</span></td>
      </tr>
      <tr>
        <td><b>P06b</b></td>
        <td>Modo degradado</td>
        <td>Parada simultánea de réplicas</td>
        <td>100% lecturas en PRIMARY</td>
        <td><span class="badge-pass">PASS</span></td>
      </tr>
      <tr>
        <td><b>P07</b></td>
        <td>Lectura crítica</td>
        <td>20 ciclos escritura + lectura</td>
        <td>20/20 datos frescos (lag &lt; 3.5 ms)</td>
        <td><span class="badge-pass">PASS</span></td>
      </tr>
      <tr>
        <td><b>P08</b></td>
        <td>Retorno de réplica</td>
        <td>Reinicio con verificación WAL</td>
        <td>Reincorporación en 12 s (&le; 60 s)</td>
        <td><span class="badge-pass">PASS</span></td>
      </tr>
    </tbody>
  </table>
  <div class="table-caption">Tabla 4: Matriz consolidada de evaluación experimental de alta disponibilidad.</div>

  <div class="formal-box">
    <strong>Evidencia P02 — Distribución de Tráfico en 100 Solicitudes:</strong>
    Sobre un ensayo de 100 peticiones dirigidas al endpoint <code>GET /api/inventario</code> a través del API Gateway, se obtuvo una tasa de <b>éxito de 100/100 (100 %)</b>. La distribución observada en cabeceras <code>X-Service-Instance</code> fue de exactamente <b>50 peticiones para <code>inventario-1</code></b> y <b>50 peticiones para <code>inventario-2</code></b>, satisfaciendo con holgura el límite máximo del 75 % por instancia.
  </div>

  <div class="formal-box">
    <strong>Evidencia P03 — Desalojo y Reincorporación en Eureka:</strong>
    Al detener <code>pedidos-2</code>, la expiración observable se produjo a los <b>17 segundos</b> (15 s de duración de lease más 3 s del barrido del timer). Durante la ventana de transición se registraron las solicitudes fallidas; una vez consumada la expulsión, el 100 % de las solicitudes posteriores fueron resueltas por <code>pedidos-1</code>. Al reiniciar el contenedor, el registro como <code>UP</code> y la recepción de tráfico se restablecieron en <b>18 segundos</b>.
  </div>

  <div class="formal-box">
    <strong>Evidencia P05 y P07 — Verificación No Circular de Base de Datos:</strong>
    El contraste contra las tablas del catálogo de PostgreSQL (<code>pg_stat_activity</code>) y los logs del motor con <code>log_statement=all</code> demostró <b>cero sentencias <code>INSERT</code> o <code>UPDATE</code> en <code>pg-replica-1</code> y <code>pg-replica-2</code></b>. En la prueba P07, el retraso de replicación (<code>replay_lag</code>) medido en el microsegundo exacto de la lectura crítica permaneció inferior a <b>3.5 milisegundos</b>, confirmando la solidez de la política implementada.
  </div>

  <h1>5. Registro de Decisiones de Arquitectura (ADR-01 a ADR-10)</h1>
  <p>
    En cumplimiento de las buenas prácticas de ingeniería, todas las decisiones estructurales del proyecto fueron formalizadas en el registro <code>docs/decisiones.md</code>:
  </p>

  <table>
    <thead>
      <tr>
        <th style="width: 14%;">Código</th>
        <th style="width: 27%;">Título / Dominio</th>
        <th style="width: 29%;">Decisión Adoptada</th>
        <th style="width: 30%;">Límite o Costo Técnico Asumido</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><b>ADR-01</b></td>
        <td>Selección de nodo BD</td>
        <td><code>AbstractRoutingDataSource</code> + AOP</td>
        <td>La decisión aplica por transacción, no por sentencia individual.</td>
      </tr>
      <tr>
        <td><b>ADR-02</b></td>
        <td>Detección de réplica</td>
        <td>Sondeo activo 5 s + timeout 1 s</td>
        <td>Requiere conexión persistente de monitoreo adicional por nodo.</td>
      </tr>
      <tr>
        <td><b>ADR-03</b></td>
        <td>Reparto de lecturas</td>
        <td>Round-robin con <code>AtomicInteger</code></td>
        <td>No considera la carga de CPU o complejidad analítica de la consulta.</td>
      </tr>
      <tr>
        <td><b>ADR-04</b></td>
        <td>Topología PostgreSQL</td>
        <td>1 primario + 2 réplicas físicas</td>
        <td>Sin promoción automática de réplica ante pérdida del primario.</td>
      </tr>
      <tr>
        <td><b>ADR-05</b></td>
        <td>Imagen PostgreSQL</td>
        <td><code>postgres:16</code> oficial + wrapper</td>
        <td>Inicialización en frío incluye ejecución de <code>pg_basebackup</code>.</td>
      </tr>
      <tr>
        <td><b>ADR-06</b></td>
        <td>Migraciones Flyway</td>
        <td>Bean <code>Flyway</code> hacia primario</td>
        <td>Se deshabilita la auto-configuración estándar de Spring Boot.</td>
      </tr>
      <tr>
        <td><b>ADR-07</b></td>
        <td>Consistencia crítica</td>
        <td>Anotación <code>@CriticalRead</code></td>
        <td>Sobrecarga el nodo primario si se abusa en lecturas comunes.</td>
      </tr>
      <tr>
        <td><b>ADR-08</b></td>
        <td>Modo de replicación</td>
        <td>Streaming WAL asíncrono</td>
        <td>Riesgo mínimo de pérdida de transacciones no replicadas si cae primario.</td>
      </tr>
      <tr>
        <td><b>ADR-09</b></td>
        <td>Módulo técnico</td>
        <td><code>libs/persistence-routing</code></td>
        <td>Cero clases de negocio; reutilizable pero acoplado a Spring.</td>
      </tr>
      <tr>
        <td><b>ADR-10</b></td>
        <td>Matriz de versiones</td>
        <td>Boot 3.5.16 + Cloud 2025.0.3 + Java 21</td>
        <td>Release train 2025.0.x en soporte final; futuros bumps exigen Boot 4.x.</td>
      </tr>
    </tbody>
  </table>
  <div class="table-caption">Tabla 5: Consolidado de las diez Decisiones de Arquitectura (ADR).</div>

  <h1>6. Enlaces de Entrega y Verificación de Reproducibilidad</h1>
  <div class="formal-box">
    <strong>Repositorio de Código Fuente en GitHub:</strong><br>
    <a href="https://github.com/DevTorres404/microservicios">https://github.com/DevTorres404/microservicios</a><br><br>
    <strong>Pull Request Formal #1 (Merged con historial completo de commits):</strong><br>
    <a href="https://github.com/DevTorres404/microservicios/pull/1">https://github.com/DevTorres404/microservicios/pull/1</a><br><br>
    <strong>Video Demostrativo del Sistema en Ejecución (YouTube):</strong><br>
    <a href="https://youtu.be/Gyf0GFP-oaI">https://youtu.be/Gyf0GFP-oaI</a>
  </div>

  <h2>6.1 Comando Único de Verificación desde Clon Limpio</h2>
  <p>
    El sistema cumple estrictamente el requisito de reproducibilidad. Cualquier evaluador puede validar la arquitectura ejecutando:
  </p>
  <pre>git clone https://github.com/DevTorres404/microservicios.git
cd microservicios
bash scripts/verify-from-scratch.sh</pre>
  <p>
    Este script levanta los 9 contenedores, confirma el estado saludable de cada proceso, verifica el registro dinámico en Eureka y valida las cabeceras de respuesta de negocio y base de datos con código de salida <code>exit 0</code>.
  </p>

</div>

</body>
</html>

</body>
</html>
"""

header_html = """
<div style="font-family: 'Inter', sans-serif; font-size: 8pt; width: 100%; display: flex; justify-content: space-between; border-bottom: 0.6pt solid #CBD5E1; padding-bottom: 2mm; margin: 0 20mm; color: #475569;">
  <span><b style="color: #111111;">UPSE</b> &nbsp;|&nbsp; Aplicaciones Distribuidas</span>
  <span>Sistema de Microservicios &nbsp;|&nbsp; <b style="color: #111111;">Grupo Torres-Tomalá-Cedeño</b></span>
</div>
"""

footer_html = """
<div style="font-family: 'Inter', sans-serif; font-size: 8pt; width: 100%; text-align: center; border-top: 0.6pt solid #CBD5E1; padding-top: 2mm; margin: 0 20mm; color: #475569;">
  Página <span class="pageNumber"></span> de <span class="totalPages"></span>
</div>
"""

def generate():
    rendered_html = html_content
    upse_data_uri = ""
    if os.path.exists("upse.png"):
        with open("upse.png", "rb") as f:
            upse_b64 = base64.b64encode(f.read()).decode("utf-8")
            upse_data_uri = f"data:image/png;base64,{upse_b64}"
    
    logo_tag = f'<img src="{upse_data_uri}" alt="UPSE">' if upse_data_uri else 'UPSE'
    rendered_html = rendered_html.replace("__UPSE_LOGO_TAG__", logo_tag)

    temp_html = "temp_informe.html"
    with open(temp_html, "w", encoding="utf-8") as f:
        f.write(rendered_html)

    print("Rendering PDF with Playwright...")
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()
        page.goto(f"file:///{os.path.abspath(temp_html)}")
        page.wait_for_load_state("networkidle")
        
        pdf_path = "informe_tecnico.pdf"
        page.pdf(
            path=pdf_path,
            format="A4",
            print_background=True,
            display_header_footer=True,
            header_template=header_html,
            footer_template=footer_html,
            margin={
                "top": "22mm",
                "bottom": "22mm",
                "left": "20mm",
                "right": "20mm"
            }
        )
        browser.close()
    
    if os.path.exists(temp_html):
        os.remove(temp_html)
    
    print(f"SUCCESS: Generated {pdf_path} ({os.path.getsize(pdf_path)} bytes)")

if __name__ == "__main__":
    generate()
