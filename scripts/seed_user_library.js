#!/usr/bin/env node

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const DEFAULT_KEY_PATH = "app/smartbookaudio-firebase-adminsdk-fbsvc-201d93fa20.json";
const DEFAULT_SEED_PATH = "data/user_library_seed.json";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/datastore";

function base64Url(input) {
  return Buffer.from(input)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
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
  const signature = crypto
    .createSign("RSA-SHA256")
    .update(unsigned)
    .sign(serviceAccount.private_key);
  return `${unsigned}.${base64Url(signature)}`;
}

async function getAccessToken(serviceAccount) {
  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion: signJwt(serviceAccount)
  });
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body
  });
  const json = await response.json();
  if (!response.ok) {
    throw new Error(`OAuth failed: ${response.status} ${JSON.stringify(json)}`);
  }
  return json.access_token;
}

function firestoreValue(value) {
  if (value === null || value === undefined) {
    return { nullValue: null };
  }
  if (value instanceof Date) {
    return { timestampValue: value.toISOString() };
  }
  if (typeof value === "string") {
    return { stringValue: value };
  }
  if (typeof value === "boolean") {
    return { booleanValue: value };
  }
  if (typeof value === "number") {
    return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  }
  if (Array.isArray(value)) {
    return { arrayValue: { values: value.map(firestoreValue) } };
  }
  return {
    mapValue: {
      fields: Object.fromEntries(Object.entries(value).map(([key, child]) => [key, firestoreValue(child)]))
    }
  };
}

function firestoreDocument(data) {
  return {
    fields: Object.fromEntries(Object.entries(data).map(([key, value]) => [key, firestoreValue(value)]))
  };
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
  const requestedUid = process.argv[4];
  const now = new Date();
  const token = await getAccessToken(serviceAccount);
  const users = seed.users || [{ uid: seed.uid, profile: seed.profile }];
  const targets = requestedUid ? users.filter((user) => user.uid === requestedUid) : users;

  if (!targets.length) {
    throw new Error(`No seed user found for uid ${requestedUid}`);
  }

  for (const user of targets) {
    const uid = user.uid;
    await patchDocument({
      token,
      projectId: serviceAccount.project_id,
      documentPath: `users/${uid}`,
      data: {
        ...user.profile,
        booksCount: seed.library.length,
        updatedAt: now
      }
    });

    await patchDocument({
      token,
      projectId: serviceAccount.project_id,
      documentPath: `users/${uid}/playback/current`,
      data: {
        ...seed.playback,
        updatedAt: now
      }
    });

    for (const entry of seed.library) {
      await patchDocument({
        token,
        projectId: serviceAccount.project_id,
        documentPath: `users/${uid}/library/${entry.bookId}`,
        data: {
          status: entry.status,
          addedAt: now,
          lastOpenedAt: now,
          lastChapterId: entry.lastChapterId,
          lastPositionSec: entry.lastPositionSec
        }
      });
    }

    console.log(`Seeded library/playback data for users/${uid}`);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
