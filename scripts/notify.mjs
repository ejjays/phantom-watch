// sends release push to fcm releases topic, app builds notification so only title travels
import crypto from "node:crypto";

const key = JSON.parse(process.env.FCM_SERVICE_ACCOUNT ?? "{}");
const tag = (process.env.TAG ?? "").trim();
const title = "New update available ✨";

if (!key.client_email || !tag) {
  console.error("FCM_SERVICE_ACCOUNT and TAG are required");
  process.exit(1);
}

function b64(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

async function accessToken() {
  const now = Math.floor(Date.now() / 1000);
  const unsigned = b64({ alg: "RS256", typ: "JWT" }) + "." + b64({
    iss: key.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  });
  const signer = crypto.createSign("RSA-SHA256");
  signer.update(unsigned);
  const jwt = unsigned + "." + signer.sign(key.private_key, "base64url");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  const body = await res.json();
  if (!body.access_token) throw new Error(`token failed: ${JSON.stringify(body)}`);
  return body.access_token;
}

const res = await fetch(`https://fcm.googleapis.com/v1/projects/${key.project_id}/messages:send`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${await accessToken()}`,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    message: {
      topic: "releases",
      data: { title },
      android: { priority: "high" },
    },
  }),
});

const text = await res.text();
console.log(res.status, text);
if (!res.ok) process.exit(1);
