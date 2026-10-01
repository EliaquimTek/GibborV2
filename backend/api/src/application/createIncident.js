const { STELLAR_CONTRACT_ID } = require("../config/env");
const { buildCreateIncidentTx, mapTransaction } = require("../domain/incident");
const walletRepository = require("../infrastructure/crossmint/walletRepository");

/**
 * Caso de uso: crea wallet (si no existe) + transacción create_incident on-chain.
 */
async function createIncident(incident) {
  const { email, incidentId, latE7, lonE7, initialHash } = incident;

  console.log(`\n══════════════════════════════════════`);
  console.log(`📡 Nuevo incidente: ${incidentId}`);
  console.log(`   email: ${email}`);
  console.log(`   lat_e7: ${latE7}, lon_e7: ${lonE7}`);
  console.log(`   hash: ${initialHash}`);
  console.log(`══════════════════════════════════════\n`);

  // 1) Asegurar que exista la wallet Stellar del usuario
  console.log("1️⃣  Creando/verificando wallet Stellar...");
  const wallet = await walletRepository.ensureStellarWallet(email);

  const walletAddress = wallet?.address || wallet?.publicKey || "unknown";
  console.log(`   Wallet address: ${walletAddress}`);

  // 2) Crear la transacción contract-call
  console.log("2️⃣  Enviando create_incident on-chain...");
  const txResult = await walletRepository.createTransaction(
    email,
    buildCreateIncidentTx(STELLAR_CONTRACT_ID, incident)
  );

  // 3) Extraer datos relevantes de la respuesta
  const txId = txResult.id || "";
  const { status, txHash, explorerLink } = mapTransaction(txResult, txId);

  console.log(`✅ TX creada: status=${status}, id=${txId}`);

  return { success: true, status, txId, txHash, explorerLink, walletAddress, raw: txResult };
}

module.exports = { createIncident };
