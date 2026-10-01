/**
 * GIBBOR Backend — Server-side Crossmint operations
 *
 * Este servidor es el intermediario entre Android y Crossmint.
 * Usa la server-side API key (sk_*) que NO puede estar en Android.
 *
 * Endpoints:
 *   POST /api/incident   — crea wallet (si no existe) + transacción on-chain
 *   GET  /api/incident/:txId — polling del estado de una transacción
 *   POST /api/evidence    — (FASE 2) recibe hash de evidencia y lo ancla on-chain
 */

const { PORT } = require("./config/env");
const app = require("./app");

app.listen(PORT, "0.0.0.0", () => {
  console.log(`\n🚀 GIBBOR Backend corriendo en http://0.0.0.0:${PORT}`);
  console.log(`   POST /api/incident     — crear incidente on-chain`);
  console.log(`   GET  /api/incident/:id — polling estado de TX`);
  console.log(`   POST /api/evidence     — (FASE 2) anclar hash evidencia`);
  console.log(`   GET  /health           — health check\n`);
});
