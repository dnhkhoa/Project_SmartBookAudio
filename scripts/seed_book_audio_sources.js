#!/usr/bin/env node

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const DEFAULT_KEY_PATH = "app/smartbookaudio-firebase-adminsdk-fbsvc-201d93fa20.json";
const DEFAULT_SEED_PATH = "data/book_audio_sources.json";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/datastore";

function base64Url(input) {
  return Buffer.from(input).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function signJwt(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: serviceAccount.client_email,
    scope: SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600
  };
  const unsigned = `${base64Url(JSON.stringify(header))}.${base64Url(JSON.stringify(claim))}`;
  const signature = crypto.createSign("RSA-SHA256").update(unsigned).sign(serviceAccount.private_key);
  return `${unsigned}.${base64Url(signature)}`;
}

async function getAccessToken(serviceAccount) {
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: signJwt(serviceAccount)
    })
  });
  const json = await response.json();
  if (!response.ok) {
    throw new Error(`OAuth failed: ${response.status} ${JSON.stringify(json)}`);
  }
  return json.access_token;
}

function firestoreValue(value) {
  if (value === null || value === undefined) return { nullValue: null };
  if (value instanceof Date) return { timestampValue: value.toISOString() };
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(firestoreValue) } };
  return { mapValue: { fields: Object.fromEntries(Object.entries(value).map(([key, child]) => [key, firestoreValue(child)])) } };
}

function firestoreDocument(data) {
  return { fields: Object.fromEntries(Object.entries(data).map(([key, value]) => [key, firestoreValue(value)])) };
}

async function patchDocument({ token, projectId, documentPath, data }) {
  const encodedPath = documentPath.split("/").map(encodeURIComponent).join("/");
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${encodedPath}`;
  const response = await fetch(url, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(firestoreDocument(data))
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Write failed for ${documentPath}: ${response.status} ${text}`);
  }
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(path.resolve(filePath), "utf8"));
}

async function main() {
  const keyPath = process.argv[2] || DEFAULT_KEY_PATH;
  const seedPath = process.argv[3] || DEFAULT_SEED_PATH;
  const serviceAccount = readJson(keyPath);
  const seed = readJson(seedPath);
  const token = await getAccessToken(serviceAccount);
  const now = new Date();

  for (const source of seed.sources) {
    const data = {
      sourceType: "youtube",
      sourceTitle: source.sourceTitle,
      sourceUrl: source.sourceUrl,
      audioUrl: source.sourceUrl,
      updatedAt: now
    };
    await patchDocument({
      token,
      projectId: serviceAccount.project_id,
      documentPath: `books/${source.bookId}`,
      data
    });
    await patchDocument({
      token,
      projectId: serviceAccount.project_id,
      documentPath: `books/${source.bookId}/chapters/chapter-01`,
      data
    });
    console.log(`Seeded audio source for books/${source.bookId}`);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
