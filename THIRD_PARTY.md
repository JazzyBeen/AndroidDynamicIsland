# Third-party components

- **sing-box 1.12.25** — GPL-3.0-or-later, [SagerNet/sing-box](https://github.com/SagerNet/sing-box/tree/v1.12.25), commit `73bfb99ebce7923c485435e4faf8571b412065a9`. Built with `scripts/BuildVpnCore.ps1`, Go 1.25.8, SagerNet gomobile 0.1.8, and Android NDK 28.0.13004108. License bundled in the APK. Transitive Go dependencies and licenses are available from the pinned source and its `go.mod` / `go.sum`.
- **Inter** — SIL Open Font License; bundled as `assets/Inter-OFL.txt`.
- **AndroidX / Jetpack Compose** — Apache-2.0, distributed through Google's Maven repository.
- **Yandex MapKit 4.45.0 Full** — Yandex SDK, distributed through Maven Central under its [SDK terms](https://yandex.ru/legal/maps_api/ru/). The SDK is not GPL software. Map display and explicit route previews require the user's own MapKit key; the free tier has limits. No NaviKit activation or shared API key is included.

The app and its integrated VPN implementation are distributed under GPL-3.0-or-later; see `LICENSE`. No proprietary VPN client binaries are included.
