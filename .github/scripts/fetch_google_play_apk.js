#!/usr/bin/env node

/**
 * Automatically uploads an Android App Bundle (.aab) to Google Play Console
 * and downloads the official Universal APK signed by Google Play App Signing.
 */

const fs = require('fs');
const path = require('path');
const { google } = require('googleapis');

const packageName = process.env.PACKAGE_NAME || 'pl.sinusoide.sinuschat';
const aabPath = process.env.AAB_PATH || 'app/build/outputs/bundle/release/app-release.aab';
const serviceAccountPath = process.env.PLAY_SERVICE_ACCOUNT_PATH;
const outputApk = process.env.OUTPUT_APK;
const track = process.env.PLAY_TRACK || 'internal';
const timeoutSec = parseInt(process.env.TIMEOUT_SEC || '600', 10);
const pollIntervalMs = 15000;

async function main() {
  if (!serviceAccountPath || !fs.existsSync(serviceAccountPath)) {
    console.error(`[-] Error: Service account JSON key file not found: ${serviceAccountPath}`);
    process.exit(1);
  }

  if (!outputApk) {
    console.error(`[-] Error: OUTPUT_APK environment variable is not defined`);
    process.exit(1);
  }

  if (!fs.existsSync(aabPath)) {
    console.error(`[-] Error: AAB bundle file not found: ${aabPath}`);
    process.exit(1);
  }

  const auth = new google.auth.GoogleAuth({
    keyFile: serviceAccountPath,
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });

  const play = google.androidpublisher({ version: 'v3', auth });

  console.log(`[+] Creating new Google Play edit for package: ${packageName}...`);
  const edit = await play.edits.insert({ packageName });
  const editId = edit.data.id;
  console.log(`[+] Edit created with ID: ${editId}`);

  let versionCode = process.env.VERSION_CODE ? parseInt(process.env.VERSION_CODE, 10) : null;
  let alreadyUploaded = false;

  console.log(`[+] Uploading AAB bundle (${(fs.statSync(aabPath).size / 1024 / 1024).toFixed(2)} MB)...`);
  try {
    const bundleRes = await play.edits.bundles.upload({
      packageName,
      editId,
      media: {
        mimeType: 'application/octet-stream',
        body: fs.createReadStream(aabPath),
      },
    });
    versionCode = bundleRes.data.versionCode;
    console.log(`[+] AAB bundle uploaded successfully! Detected versionCode: ${versionCode}`);
  } catch (err) {
    const msg = (err.message || '').toLowerCase();
    if (msg.includes('already been used') || msg.includes('version code')) {
      console.log(`[!] Note: This versionCode has already been uploaded to Google Play.`);
      alreadyUploaded = true;
    } else {
      throw err;
    }
  }

  if (!alreadyUploaded) {
    console.log(`[+] Assigning version ${versionCode} to track '${track}'...`);
    await play.edits.tracks.update({
      packageName,
      editId,
      track,
      requestBody: {
        releases: [
          {
            name: `SinusChat ${versionCode}`,
            versionCodes: [String(versionCode)],
            status: 'completed',
          },
        ],
      },
    });

    console.log(`[+] Committing edit ${editId}...`);
    await play.edits.commit({ packageName, editId });
    console.log(`[+] Edit committed! Google Play is now generating the Universal APK...`);
  } else {
    try {
      await play.edits.delete({ packageName, editId });
    } catch (_) {}
  }

  // Poll for Universal APK signed by Google
  console.log(`[+] Waiting for Google Play to generate the signed Universal APK...`);
  const startTime = Date.now();
  let downloadId = null;
  let attempt = 0;

  while (Date.now() - startTime < timeoutSec * 1000) {
    attempt++;
    await new Promise((r) => setTimeout(r, pollIntervalMs));
    try {
      const res = await play.generatedapks.list({
        packageName,
        versionCode,
      });

      const list = res.data.generatedApks || [];
      for (const item of list) {
        if (item.generatedUniversalApk?.downloadId) {
          downloadId = item.generatedUniversalApk.downloadId;
          break;
        }
      }

      if (downloadId) {
        console.log(`[+] Found ready Universal APK! downloadId: ${downloadId}`);
        break;
      }
    } catch (err) {
      console.log(`[!] Waiting for APK generation (attempt #${attempt}): ${err.message}`);
    }
    console.log(`[+] Still processing... re-checking in 15s...`);
  }

  if (!downloadId) {
    console.error(`[-] Error: Timeout reached (${timeoutSec}s) waiting for Universal APK.`);
    process.exit(1);
  }

  // Download the APK file
  console.log(`[+] Downloading Google-signed Universal APK to: ${outputApk}...`);
  fs.mkdirSync(path.dirname(path.resolve(outputApk)), { recursive: true });

  const dl = await play.generatedapks.download(
    {
      packageName,
      versionCode,
      downloadId,
    },
    { responseType: 'stream' }
  );

  const dest = fs.createWriteStream(outputApk);
  await new Promise((resolve, reject) => {
    dl.data.pipe(dest);
    dest.on('finish', resolve);
    dest.on('error', reject);
  });

  const stats = fs.statSync(outputApk);
  console.log(`[SUCCESS] Universal APK downloaded successfully (${(stats.size / 1024 / 1024).toFixed(2)} MB)!`);
  console.log(`[SUCCESS] File: ${outputApk} is signed with Google Play App Signing key.`);
}

main().catch((err) => {
  console.error(`[-] Fatal error:`, err.message || err);
  process.exit(1);
});
