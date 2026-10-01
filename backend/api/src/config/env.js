require("dotenv").config();

const {
  CROSSMINT_SERVER_API_KEY,
  CROSSMINT_BASE_URL,
  STELLAR_CONTRACT_ID,
  PORT = 3001,
} = process.env;

if (!CROSSMINT_SERVER_API_KEY || CROSSMINT_SERVER_API_KEY === "sk_staging_REPLACE_ME") {
  console.error("⚠️  CROSSMINT_SERVER_API_KEY no configurada. Edita backend/.env");
  process.exit(1);
}

console.log("🔑 Server API key cargada:", CROSSMINT_SERVER_API_KEY.slice(0, 20) + "...");
console.log("🌐 Crossmint base URL:", CROSSMINT_BASE_URL);
console.log("📄 Contract ID:", STELLAR_CONTRACT_ID);

module.exports = {
  CROSSMINT_SERVER_API_KEY,
  CROSSMINT_BASE_URL,
  STELLAR_CONTRACT_ID,
  PORT,
};
