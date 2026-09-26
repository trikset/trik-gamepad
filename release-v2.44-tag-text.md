TRIK Gamepad 2.44 — clear video status and F-Droid readiness

What's new

This release makes it easier to see what your robot is doing. If the video from the robot's camera cannot load within ten seconds, a clear badge now appears in the picture area — no more staring at an empty spot and wondering. A new crossed-eye symbol shows at a glance that there is no video, so you always know the state of the connection. On the inside, the app is now prepared for distribution through the F-Droid store, with a simpler version numbering scheme.

The app still works fully offline: no account, no internet — just your local Wi-Fi to the robot. You drive the robot from the touchscreen, and the video comes straight from the robot's camera.

Key improvements

- If the video feed cannot start, you now see a clear "no video" badge after ten seconds instead of an empty area.
- A new crossed-eye symbol on the HUD shows at a glance that there is no video, and the on-screen icons stay better synchronized.
- The app is now prepared for the F-Droid app store: store pages for French, German and Vietnamese were added.
- App versioning is now handled from a single source, which makes builds simpler and more reliable.

---

For developers

### Version

| Version | versionCode | minSdk | targetSdk |
|---------|-------------|--------|-----------|
| 2.44 | 210244 | Android 5.0 Lollipop (API 21) | 36 |

### Major changes

- feat: video load timeout with placeholder badge and button mapping docs (#40)
- feat: single-source app version with F-Droid reproducible builds and de/fr/vi locale metadata (#42)
- ci: publish GitHub release automatically from signed release tags (#43)
- feat: crossed-eye glyph for no-video, sync centre/chip eye, README restructure (#41)
- feat: add F-Droid fastlane metadata, bump to v2.44 (#39)

Contributors: [@iakov](https://github.com/iakov)

Detailed comparison with previous release v2.43: <https://github.com/trikset/trik-gamepad/compare/v2.43...v2.44>