# Contributing to InspectMC

Thanks for helping improve InspectMC.

## Before opening a pull request

- Keep each pull request focused on one coherent change.
- Do not bundle unrelated formatting or refactors with a functional change.
- For mod-code changes, explain the player/developer-visible effect and how it was tested.
- Preserve Fabric and NeoForge behavior unless the change is intentionally loader-specific.
- Do not present experimental Minecraft 26.2 or 26.3 work as part of the public 1.21.1 release.
- Keep the changelog release-oriented. Do not add temporary development-only fixes as public release notes.

## Documentation changes

The public GitHub Pages documentation lives in `site/`. Keep command syntax aligned with the current implementation and README.

## Security

Do not disclose security vulnerabilities in public issues or pull requests. Follow `SECURITY.md`.

## Review

All changes remain subject to maintainer review. CODEOWNERS marks LevelsFR as the owner of the repository. Branch protection should require pull requests and code-owner review before changes reach `main`.
