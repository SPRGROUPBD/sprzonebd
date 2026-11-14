/**
 * SPR ZONE BD — Private Server-Side Courier Dispatch Proxy (Cloudflare Worker)
 *
 * SECURITY:
 * - Steadfast Api-Key / Secret-Key and Pathao Client ID / Client Secret MUST ONLY be stored
 *   as Cloudflare Worker Secrets (`wrangler secret put ...`), NEVER in website/app code.
 * - Every request requires a valid Firebase Auth ID Token for mail.sajjadahmed@gmail.com
 *   (or custom claim `admin === true`) from Firebase project `e-commarcebd`.
 */

const FIREBASE_PROJECT_ID = "e-commarcebd";
const ADMIN_EMAIL = "mail.sajjadahmed@gmail.com";
const STEADFAST_BASE = "https://portal.packzy.com/api/v1";
const PATHAO_BASE = "https://api-hermes.pathao.com";

function corsHeaders(origin = "*") {
  return {
    "Access-Control-Allow-Origin": origin || "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Max-Age": "86400",
  };
}

function jsonResponse(data, status = 200, origin = "*") {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...corsHeaders(origin),
    },
  });
}

function base64UrlToUint8Array(b64u) {
  const b64 = b64u.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((b64u.length + 3) % 4);
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

async function verifyFirebaseAdminToken(authHeader, env) {
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    throw new Error("Missing Authorization Bearer token");
  }
  const token = authHeader.slice(7).trim();
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("Invalid JWT format");

  const header = JSON.parse(new TextDecoder().decode(base64UrlToUint8Array(parts[0])));
  const payload = JSON.parse(new TextDecoder().decode(base64UrlToUint8Array(parts[1])));

  const projectId = env.FIREBASE_PROJECT_ID || FIREBASE_PROJECT_ID;
  const nowSec = Math.floor(Date.now() / 1000);
  if (payload.aud !== projectId) throw new Error("Token audience mismatch");
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) throw new Error("Token issuer mismatch");
  if (!payload.exp || payload.exp < nowSec) throw new Error("Token expired");
  if (!payload.sub) throw new Error("Missing subject");

  const jwkRes = await fetch(
    "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
  );
  if (!jwkRes.ok) throw new Error("Unable to fetch Firebase public JWKs");
  const { keys } = await jwkRes.json();
  const jwk = keys.find((k) => k.kid === header.kid);
  if (!jwk) throw new Error("Matching Firebase signing key not found");

  const cryptoKey = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const data = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
  const sig = base64UrlToUint8Array(parts[2]);
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", cryptoKey, sig, data);
  if (!valid) throw new Error("Invalid Firebase token signature");

  const adminEmail = (env.ADMIN_EMAIL || ADMIN_EMAIL).toLowerCase();
  const emailMatch = String(payload.email || "").toLowerCase() === adminEmail && payload.email_verified !== false;
  const claimAdmin = payload.admin === true;
  if (!emailMatch && !claimAdmin) {
    throw new Error("Unauthorized: Admin access required");
  }
  return payload;
}

function normalizeBdPhone(raw = "") {
  const digits = String(raw).replace(/\D/g, "");
  if (digits.startsWith("8801") && digits.length === 13) return digits.slice(2);
  if (digits.startsWith("01") && digits.length === 11) return digits;
  return digits || String(raw).trim();
}

async function dispatchSteadfast(body, env) {
  if (!env.STEADFAST_API_KEY || !env.STEADFAST_SECRET_KEY) {
    throw new Error("Server missing STEADFAST_API_KEY or STEADFAST_SECRET_KEY secrets");
  }
  const res = await fetch(`${STEADFAST_BASE}/create_order`, {
    method: "POST",
    headers: {
      "Api-Key": env.STEADFAST_API_KEY,
      "Secret-Key": env.STEADFAST_SECRET_KEY,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      invoice: String(body.invoice || "").trim(),
      recipient_name: String(body.recipient_name || "").trim(),
      recipient_phone: normalizeBdPhone(body.recipient_phone || ""),
      recipient_address: String(body.recipient_address || "").trim(),
      cod_amount: Number(body.cod_amount || 0),
      note: String(body.note || "").trim(),
    }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || (data.status && Number(data.status) >= 400)) {
    throw new Error(data.message || `Steadfast API error HTTP ${res.status}`);
  }
  return {
    ok: true,
    courier: body.courier || "Steadfast",
    provider: "Steadfast",
    consignment: data.consignment || data,
    raw: data,
  };
}

async function dispatchPathao(body, env) {
  if (!env.PATHAO_CLIENT_ID || !env.PATHAO_CLIENT_SECRET || !env.PATHAO_USERNAME || !env.PATHAO_PASSWORD || !env.PATHAO_STORE_ID) {
    // Fallback to Steadfast if Pathao merchant credentials/store_id are not fully configured
    return dispatchSteadfast({ ...body, courier: "Steadfast" }, env);
  }
  const tokenRes = await fetch(`${PATHAO_BASE}/aladdin/api/v1/issue-token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      client_id: env.PATHAO_CLIENT_ID,
      client_secret: env.PATHAO_CLIENT_SECRET,
      username: env.PATHAO_USERNAME,
      password: env.PATHAO_PASSWORD,
      grant_type: "password",
    }),
  });
  const tokenJson = await tokenRes.json().catch(() => ({}));
  if (!tokenRes.ok || !tokenJson.access_token) {
    throw new Error(tokenJson.message || "Pathao auth failed");
  }
  const orderRes = await fetch(`${PATHAO_BASE}/aladdin/api/v1/orders`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${tokenJson.access_token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      store_id: Number(env.PATHAO_STORE_ID),
      merchant_order_id: String(body.invoice || ""),
      recipient_name: String(body.recipient_name || ""),
      recipient_phone: normalizeBdPhone(body.recipient_phone || ""),
      recipient_address: String(body.recipient_address || ""),
      delivery_type: 48,
      item_type: 2,
      special_instruction: String(body.note || ""),
      item_quantity: 1,
      item_weight: 0.5,
      amount_to_collect: Number(body.cod_amount || 0),
    }),
  });
  const orderJson = await orderRes.json().catch(() => ({}));
  if (!orderRes.ok) {
    throw new Error(orderJson.message || `Pathao order failed HTTP ${orderRes.status}`);
  }
  const cId = orderJson?.data?.consignment_id || "";
  return {
    ok: true,
    courier: "Pathao",
    provider: "Pathao",
    consignment: { consignment_id: cId, tracking_code: cId, status: orderJson?.data?.order_status || "Pickup Requested" },
    raw: orderJson,
  };
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "*";
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(origin) });
    }

    try {
      await verifyFirebaseAdminToken(request.headers.get("Authorization"), env);
      const url = new URL(request.url);

      if (request.method === "GET" && url.pathname.endsWith("/health")) {
        return jsonResponse({
          ok: true,
          provider: "Steadfast",
          steadfastConfigured: Boolean(env.STEADFAST_API_KEY && env.STEADFAST_SECRET_KEY),
          pathaoConfigured: Boolean(env.PATHAO_CLIENT_ID && env.PATHAO_CLIENT_SECRET && env.PATHAO_STORE_ID),
        }, 200, origin);
      }

      if (request.method === "POST" && url.pathname.endsWith("/create-order")) {
        const body = await request.json();
        const courier = String(body.courier || "Steadfast");
        const result = courier === "Pathao" && env.PATHAO_STORE_ID
          ? await dispatchPathao(body, env)
          : await dispatchSteadfast(body, env);
        return jsonResponse(result, 200, origin);
      }

      if (request.method === "GET" && url.pathname.endsWith("/track")) {
        if (!env.STEADFAST_API_KEY || !env.STEADFAST_SECRET_KEY) {
          throw new Error("Server missing STEADFAST_API_KEY or STEADFAST_SECRET_KEY secrets");
        }
        const invoice = url.searchParams.get("invoice");
        const cid = url.searchParams.get("consignmentId");
        const endpoint = cid
          ? `${STEADFAST_BASE}/status_by_cid/${encodeURIComponent(cid)}`
          : `${STEADFAST_BASE}/status_by_invoice/${encodeURIComponent(invoice || "")}`;
        const res = await fetch(endpoint, {
          headers: {
            "Api-Key": env.STEADFAST_API_KEY,
            "Secret-Key": env.STEADFAST_SECRET_KEY,
          },
        });
        const data = await res.json().catch(() => ({}));
        return jsonResponse({ ok: res.ok, ...data }, res.status, origin);
      }

      return jsonResponse({ error: "Not found. Use POST /create-order, GET /track, or GET /health" }, 404, origin);
    } catch (err) {
      return jsonResponse({ ok: false, error: err.message || "Request failed" }, 401, origin);
    }
  },
};
