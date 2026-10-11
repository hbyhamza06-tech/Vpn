# Free VPN (Android, WireGuard)

Kotlin + Jetpack Compose app that opens a real WireGuard tunnel through `VpnService`
(via the open-source `wireguard-android` tunnel library) to servers **you operate**.

> Status: written without access to a build environment. It has NOT been compiled or run.
> Expect to fix small API mismatches on first build (see "Verify on first build").

## Selected provider (required documentation)
| Item | Detail |
|---|---|
| Provider | Self-hosted WireGuard servers (you) + open-source client library |
| Library | `com.wireguard.android:tunnel:1.0.20230706` (wireguard-android). Confirm its license file/terms yourself before shipping |
| Developer docs | https://www.wireguard.com/ , https://git.zx2c4.com/wireguard-android/ |
| Permission to integrate | Open-source library; no third-party VPN service is resold. You must own/rent the servers and comply with your host's acceptable-use terms (some VPS hosts restrict VPN/relay use) |
| Free-tier limits | Set by you (bandwidth caps, peers per device). Disclose them in Privacy policy |
| Credentials | None in the APK. Each device generates its own key pair (stored encrypted). Server public keys come from your API |
| Trademark | "WireGuard" is a registered trademark of Jason A. Donenfeld; keep the notice in About |

AdMob: not included. Add only after checking Google Play policy for VPN apps and your own terms.
Google Play: VPN apps must declare use of `VpnService`, publish a privacy policy, and not collect traffic content.

## Setup
1. Stand up servers and the provisioning API: `docs/BACKEND.md`.
2. Set `VPN_API_BASE=https://your-api` in `gradle.properties` (or `~/.gradle/gradle.properties`).
3. Open the folder in Android Studio (Koala+, JDK 17). Let it create the Gradle wrapper and sync.
4. Run on a **physical device** (emulators handle VPN poorly).
5. `./gradlew test` runs unit tests.

## Behavior
- "Connected" shows only after a WireGuard handshake completes (12 s timeout, 3 retries if auto-reconnect is on).
- Tunnel routes `0.0.0.0/0` and `::/0` and sets the DNS from your server, to avoid DNS/IPv6 leaks.
- Best server = lowest HTTPS round-trip to each server's `probeUrl`.
- Wi-Fi/mobile switching: WireGuard roams endpoints itself; the controller retries after loss if auto-reconnect is on.

## Verify on first build
- `Statistics.peer(key)?.latestHandshakeEpochMillis()` / `rxBytes()` exist in your library version (they do in recent releases; adjust if not).
- `GoBackend.VpnService.prepare(context)` import path.
- Add a foreground-service notification if you want a persistent "VPN active" notification.

## Release
Create a keystore, add a `signingConfigs` block, `./gradlew bundleRelease`, upload the AAB to Play Console, complete the VPN declaration and Data safety form.

## v2 additions
Favorites (star a location), DNS provider choice, connect-on-launch, permission help dialog, refreshed palette.
Deliberately NOT included from the earlier prototype: simulated stats/leak test, hardcoded servers, a kill-switch toggle
(Android only enforces this via system "Always-on VPN" + "Block connections without VPN"; Settings links to it),
and OpenVPN/IKEv2 options (only WireGuard is implemented).
