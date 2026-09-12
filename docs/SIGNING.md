# Release signing

The official APK is signed with TQNL's Countdown release key. The private key is not in this repository. The public certificate is [signing-certificate.pem](signing-certificate.pem).

Certificate SHA-256:

```
adb912ce7a39e255368478fa24cb4c64d58bd3344ce6920e8775560c9299f3ae
```

Verify a downloaded APK using Android SDK Build Tools:

```sh
apksigner verify --verbose --print-certs countdown-0.3.0.apk
```

Compare its file hash with the release's SHA256SUMS as a separate integrity check. A valid signature identifies the signing key and detects changes after signing; it is not a store review or a guarantee that the app has no bugs.

Source-built APKs signed with your own key cannot update an official installation directly. Uninstalling first clears the app's local settings and widgets. The application ID is `io.github.tqnl.countdown`.
