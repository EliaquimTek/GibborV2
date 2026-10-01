const { CROSSMINT_BASE_URL, CROSSMINT_SERVER_API_KEY } = require("../../config/env");

/**
 * Helper: HTTP request a Crossmint usando la server-side API key.
 */
async function crossmintRequest(method, path, body = null) {
  const url = `${CROSSMINT_BASE_URL}${path}`;
  const options = {
    method,
    headers: {
      "Content-Type": "application/json",
      "X-API-KEY": CROSSMINT_SERVER_API_KEY,
    },
  };

  if (body) {
    options.body = JSON.stringify(body);
  }

  console.log(`→ ${method} ${url}`);
  if (body) console.log("  Body:", JSON.stringify(body, null, 2));

  const res = await fetch(url, options);
  const text = await res.text();

  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text };
  }

  console.log(`← ${res.status}`, JSON.stringify(json, null, 2));

  if (!res.ok) {
    const err = new Error(`Crossmint HTTP ${res.status}`);
    err.status = res.status;
    err.body = json;
    throw err;
  }

  return json;
}

module.exports = { crossmintRequest };
