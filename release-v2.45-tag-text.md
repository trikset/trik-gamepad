TRIK Gamepad 2.45 — F-Droid readiness and version cleanup

What's new

This release lays the groundwork for distributing the app through the F-Droid store. The app now links its privacy policy directly from the main page, and the version numbering is handled from a single source — making builds simpler and more reliable. No changes to how you drive the robot: still fully offline, no account, just your local Wi-Fi.

Key improvements

- The privacy policy is now linked from the project page for full transparency.
- Internal version handling is consolidated into a single source, preparing for F-Droid store submission.

---

For developers

### Version

| Version | versionCode | minSdk | targetSdk |
|---------|-------------|--------|-----------|
| 2.45 | 210245 | Android 5.0 Lollipop (API 21) | 36 |

### Major changes

- feat: read versionCode from version.properties, add VERSION_CODE field (#44)
- feat: store fdroiddata metadata in-repo, sync via version_manager.py bump (#44)
- chore: bump v2.44→v2.45 for next release (#44)

Contributors: [@iakov](https://github.com/iakov)

Detailed comparison with previous release v2.44: <https://github.com/trikset/trik-gamepad/compare/v2.44...v2.45>