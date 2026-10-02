# Get your APK in ~10 minutes

The repo is already committed locally at `/home/user/vireo` (branch `main`).

## Step 1 — Create an empty GitHub repo
Go to https://github.com/new → name it **vireo** → **Do NOT** add README/.gitignore → Create.

## Step 2 — Push
Copy the repo URL, then in this chat send me:

    push https://github.com/YOURNAME/vireo.git  TOKEN

(Token = a GitHub fine-grained PAT with **Contents: Read & write** on that repo,
from https://github.com/settings/tokens — it is only used for this one push.)

Or run it yourself on your own machine after downloading the folder:

```bash
cd vireo
git remote add origin https://github.com/YOURNAME/vireo.git
git push -u origin main
```

## Step 3 — Download the APK
Pushing to `main` triggers the **Build APK** workflow automatically.

GitHub → your repo → **Actions** tab → latest run → wait ~6–8 min →
**Artifacts** section at the bottom → download **vireo-release-apk**.

Unzip it, copy `app-release.apk` to your phone, tap to install
(allow "Install unknown apps" when prompted).

## Step 4 — Emulator run (optional)
Actions tab → **Emulator Test** → *Run workflow*. It boots a Pixel 6 on
Android 14, installs the app, launches it, and uploads a screenshot artifact
so you can see it actually running.

## Notes
- The release APK is signed with the debug key so it installs without extra setup.
  For Play Store you'll need a real keystore — tell me and I'll add signing secrets to the workflow.
- First CI run is slowest (downloads SDK + dependencies); later runs are cached.
