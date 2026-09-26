TRIK Gamepad 2.46 — F-Droid ready, versionCode-based assets, pre-release validation

What's new

This release makes the app ready for the F-Droid free app store: the metadata has been cleaned up per the maintainer review, reproducible builds are verified, and the release pipeline is fully automated. The app now links its privacy policy directly from the project page for full transparency.

On the build side, the version numbering is now handled from a single source, and the release asset naming uses the version code instead of the API level — making it robust to future SDK bumps without manual URL updates. A pre-release validation script catches common mistakes before tagging.

Still fully offline: no account, no internet — just your local Wi-Fi to the robot.

Key improvements

- The privacy policy is now linked from the project page for full transparency.
- App versioning is consolidated into a single source (version.properties), making builds simpler and more reliable.
- Release asset names now use the version code (e.g. TRIKGamepad-210246-release.apk), automatically future-proof for minSdk changes.
- A pre-release validation script (version_manager.py check) runs 7 automated gates to catch F-Droid metadata issues before tagging.
- The app is prepared for the F-Droid store: reproducible builds verified, metadata follows the canonical format, signing key registered.

---

For developers

### Version

| Version | versionCode | minSdk | targetSdk |
|---------|-------------|--------|-----------|
| 2.46 | 210246 | Android 5.0 Lollipop (API 21) | 36 |

### Major changes

- feat: read versionCode from version.properties, add VERSION_CODE field (#44)
- feat: store fdroiddata metadata in-repo, sync via version_manager.py bump (#44)
- feat: add fdroiddata validation to version_manager.py check (7 gates) (#45)
- fix: asset naming uses versionCode, drop API21 suffix from Binaries (#45)
- docs: comprehensive F-Droid submission reference with error catalog

Contributors: [@iakov](https://github.com/iakov)

Detailed comparison with previous release v2.44: <https://github.com/trikset/trik-gamepad/compare/v2.44...v2.46>
