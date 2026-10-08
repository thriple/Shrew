# Shrew

Grocery price tracker for Android (codename). Scan each item as it goes in the cart; Shrew logs the
price for the store you're in and says straight away whether it's **new**, **cheaper**, the **same**,
**more expensive** or **way more expensive** than last time, and where you paid less.

## Getting the app

Every push to `main` is built by GitHub Actions (`.github/workflows/build.yml`) and published on the
repo's **Releases** page. On the phone, open `/releases/latest`, download `shrew-N.apk`, tap it, and
allow "install unknown apps" when asked. New builds install over old ones and keep your data.

## How it works

- **Scan:** one tap captures a frame (kept in memory, never saved), looks for a UPC/EAN barcode, then
  reads the text on the same frame (ML Kit, on-device).
- **Barcode known:** straight to the price. **Barcode new:** the name is looked up in Open Food Facts
  (free, needs internet), falling back to the name read off the pack; you confirm or edit it.
- **No barcode:** the words read off the pack become chips; the name is matched against your own
  list and you confirm the match. Linking a barcode makes it instant next time.
- **Price:** numbers read off the shelf tag are offered as chips (unit prices are ignored); confirm
  or type it, and flag sale prices so they stay out of the usual price.
- **Verdict:** compared with the last price at any store. A rise above the margin (default $1.00,
  widened to the spread of the item's own earlier prices) is "way more expensive".
- **Location:** one foreground fix at trip start, matched to a saved store within 150 m. Never in
  the background. Data stays on the phone (SQLite).

## Layout

- `app/src/main/java/com/shrew/data` — storage (SQLite), price/name parsing, fuzzy matching, lookup
- `app/src/main/java/com/shrew/scan` — CameraX capture, ML Kit, location fix
- `app/src/main/java/com/shrew/ui` — the soft dark design system and the screens (Jetpack Compose)

## Notes

- Fonts are Type Union Yoshida cuts supplied by the owner. Check the licence before distributing
  the app: embedding fonts in an app usually needs an app licence.
- `debug.keystore` is a throwaway signing key kept in the repo on purpose so every build is signed alike.
