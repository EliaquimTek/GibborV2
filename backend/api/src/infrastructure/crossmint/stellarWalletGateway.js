const {
  createCrossmint,
  CrossmintWallets,
  StellarWallet,
  WalletNotAvailableError,
} = require("@crossmint/wallets-sdk");
const {
  CROSSMINT_SERVER_API_KEY,
  CROSSMINT_SIGNER_SECRET,
  STELLAR_CONTRACT_ID,
} = require("../../config/env");

/**
 * Wallets Stellar de Crossmint firmadas por un "server signer".
 *
 * El SDK deriva la llave de firma a partir de CROSSMINT_SIGNER_SECRET y firma
 * localmente; Crossmint solo recibe la transacción ya autorizada.
 */
const crossmint = createCrossmint({ apiKey: CROSSMINT_SERVER_API_KEY });
const crossmintWallets = CrossmintWallets.from(crossmint);

const serverSigner = () => ({ type: "server", secret: CROSSMINT_SIGNER_SECRET });

// Wallets ya abiertas en este proceso (email → wallet con el signer listo)
const walletCache = new Map();

function foreignSignerError(email) {
  const err = new Error(
    `El correo ${email} ya tiene una wallet creada con otro firmante; ` +
      "este backend no puede firmar con ella. Usa otro correo o el CROSSMINT_SIGNER_SECRET con el que se creó."
  );
  err.status = 409;
  return err;
}

/**
 * Obtiene la wallet Stellar del usuario: la abre si ya existe y solo la crea
 * la primera vez. Así cada pulsación del botón reutiliza la misma wallet.
 */
async function getOrCreateUserWallet(email) {
  const cached = walletCache.get(email);
  if (cached) return cached;

  let wallet;
  try {
    wallet = await crossmintWallets.getWallet(`email:${email}:stellar:smart`, { chain: "stellar" });
    console.log("   Wallet existente encontrada");
  } catch (err) {
    if (!(err instanceof WalletNotAvailableError)) throw err;
    console.log("   El usuario no tiene wallet: creando una nueva");
    wallet = await crossmintWallets.createWallet({
      chain: "stellar",
      owner: `email:${email}`,
      recoveryMethods: [serverSigner()],
    });
  }

  try {
    await wallet.useSigner(serverSigner());
  } catch (err) {
    // La wallet existe pero fue creada con otro firmante (otro secreto o api-key)
    console.error("   useSigner falló:", err.message);
    throw foreignSignerError(email);
  }

  walletCache.set(email, wallet);
  return wallet;
}

/**
 * Ejecuta un método del contrato GIBBOR firmado con la wallet del usuario.
 * Espera la confirmación on-chain y devuelve { hash, explorerLink, transactionId }.
 */
async function callContract(wallet, { method, args }) {
  console.log(`→ contract ${STELLAR_CONTRACT_ID}.${method}`, JSON.stringify(args));
  const tx = await StellarWallet.from(wallet).sendTransaction({
    contractId: STELLAR_CONTRACT_ID,
    method,
    args,
  });
  console.log(`← tx ${tx.transactionId} hash=${tx.hash}`);
  return tx;
}

module.exports = { getOrCreateUserWallet, callContract };
