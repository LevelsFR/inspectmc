# Release workflow

InspectMC 1.0.3 is prepared for six JARs: Fabric and NeoForge for Minecraft 1.21.1, 26.2 and 26.3.

## Local verification

Use Java 21 and Java 25, then run:

```powershell
.\gradlew.bat -Pjava21_home="C:\path\to\jdk-21" -Pjava25_home="C:\path\to\jdk-25" verifyRelease
```

`verifyRelease` runs the common checks, builds all six artifacts, and checks their Minecraft and InspectMC versions. `buildAllVersions` also stages them under `build/releases/latest`.

The 1.21.1 Cobblemon integration compiles against OS Cobblemon Library 1.5.2. GitHub Actions checks out a pinned OSCL source commit and publishes its compile-only artifacts to the runner's Maven Local before building InspectMC. The release JARs do not bundle OSCL. For local source builds, publish OSCL 1.5.2 to Maven Local first.

## GitHub Actions

- **Build all versions** runs for pushes to `main` and pull requests. It does not publish files.
- **Prepare and publish release** is manual. Its default run only builds all six JARs and saves them as a short-lived Actions artifact.
- To publish, run it from `main`, enter the version matching all three `gradle.properties` files, and select the destinations. CurseForge receives six separately versioned files with the matching Minecraft version and loader metadata.
- **Deploy wiki to GitHub Pages** is manual so source pushes do not publish the documentation site automatically.

## Repository settings for marketplace uploads

Add these under **Settings → Secrets and variables → Actions** before selecting the corresponding destination:

| Name | Type | Value |
| --- | --- | --- |
| `CURSEFORGE_TOKEN` | Secret | CurseForge API token with permission to upload to InspectMC project `1703471` |

GitHub Releases use the workflow's built-in `GITHUB_TOKEN`; no separate token is needed. The CurseForge credential is checked only when that destination is selected. Never commit or paste API tokens into source files.

CurseForge uploads target the existing InspectMC project. Publishing is never triggered by a push or pull request.
