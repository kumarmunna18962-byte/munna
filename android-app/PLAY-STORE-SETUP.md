# DC Calculator Android build

This project creates a basic Android WebView shell for the existing DC Calculator website.

- Application ID: `com.munna.dccalculator`
- Minimum Android version: Android 6.0 (API 23)
- Website: https://dc-calculator-share.onrender.com/DC_Calculator.html
- The VIP payment interface remains paused on the website.

## Build a test APK

The GitHub Actions workflow **DC Calculator Android build** creates a debug APK artifact. It is for device testing, not Play Store publishing.

## Build a signed Play Store AAB

A signed Android App Bundle is only built after adding these four repository Actions secrets under Settings → Secrets and variables → Actions:

- `ANDROID_KEYSTORE_BASE64`: base64 contents of your release keystore
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep the keystore and passwords private and backed up securely. Never commit them to this repository. Losing the signing key can make future app updates difficult or impossible if you are not using Play App Signing appropriately.

## Before publishing

A build is not the same as Google Play approval. Complete Play Console account registration, store listing, screenshots, content rating, Data safety answers, privacy policy review, and required testing. Google may reject a thin website wrapper; ensure the app offers a reliable, useful mobile experience and complies with all current Play policies. Review the app on real devices before publishing.
