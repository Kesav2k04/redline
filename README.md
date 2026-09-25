# Redline

Reads a rental lease on the phone and points at the clauses that could cost you money.

Built by a student for the Next Gen Award and the RevenueCat Design Award at RevenueCat Shipaton 2026.

Everything that touches the lease runs directly on the device. No account and no server upload. Open the PDF your landlord sent, scan paper pages with the camera, or paste the text: text extraction, OCR, and risk analysis happen locally on the phone. The lease never leaves the device. The app connects to RevenueCat only to display pricing and process payments.

<p align="center">
  <img src="docs/start.png" alt="Start screen: 3D tilting lease hero, three ways to import, and sample lease" width="31%" />
  <img src="docs/report.png" alt="Report screen: 0-100 radial risk gauge, stated exposure, and void clause notices" width="31%" />
  <img src="docs/bento.png" alt="Bento grid: category risk breakdown across money, entry, exit, and upkeep" width="31%" />
</p>

<p align="center">
  <img src="docs/document-open.png" alt="Document viewer: physical ink highlighter marks under clauses with margin jump rail" width="31%" />
  <img src="docs/draft.png" alt="Negotiation drafter: formal email and concise WhatsApp counter-proposals" width="31%" />
  <img src="docs/compare.png" alt="Compare screen: side-by-side lease comparison with butterfly risk bars" width="31%" />
</p>

The scan always runs to completion and the count is honest. The first flagged clause is free in full, with the exact sentence it came from, the financial calculation, and what to ask for instead. Paying opens the remaining clauses, every finding in the marked-up lease, and the reply drafter. Renter Pro also compares two saved leases side by side.

## In five lines

- **What it does:** Reads a lease on the device (PDF, in-app CameraX scan, file share, or paste) and checks 38 rules across late fees, deposit locks, liability waivers, entry rights, and upkeep traps, quoting the exact clause and citing local tenant statutes for six jurisdictions.
- **Visceral UI:** Interactive 3D tilting lease hero with spring physics, 0-100 radial risk gauge, 4-category bento grid, physical marker ink document viewer with margin jump rail, counter-proposal drafter, and side-by-side comparison with butterfly bars.
- **Free:** The scan, 0-100 risk score, category bento, honest clause count, the first flagged clause in full, subjects of all locked clauses, and the complete 38-rule inspection catalog.
- **Paid, priced by the store:** a single-lease pass for the report on screen, or Renter Pro for every lease you scan plus comparison. Both are read from the RevenueCat offering, and Pro is the `full_report` entitlement.
- **Privacy:** the lease text never leaves the phone and there is no account. The app talks to RevenueCat for the price and the payment, and ML Kit sends Google its usage diagnostics (device, app and performance details, not the text it reads). Pro restores after a reinstall with no tap.

## Product experience

### In-app camera scanner
Photograph paper leases directly inside the app with CameraX. ML Kit text recognition reads the preview on the phone, outlines the text it finds, and signals when the page is steady enough to shoot. It has a torch, a haptic shutter, and takes page after page into one lease.

### Interactive 3D lease hero
The home screen features a 3D contract card rendered in Compose graphics layers with spatial spring physics. Dragging tilts the pages, and they spring back on release. A scanning beam marks lines red and amber as it passes, and two floating figures drift with the tilt.

### Radial risk gauge and bento grid
The report leads with a 0 to 100 risk gauge drawn with a sweep gradient:
- **Low Risk (0-29):** Standard terms with fair protections.
- **Caution (30-59):** Unbalanced clauses that warrant negotiation.
- **Toxic Clauses (60-100):** Several serious clauses.

Under the gauge, the bento grid splits risk into four distinct pillars: Financial exposure, Privacy & entry, Termination traps, and Maintenance shifting.

### Stated financial exposure and void clauses
Redline adds up what the flagged clauses put at stake in the lease's own terms: months of rent held as deposit or charged for leaving early, plus sums the lease states. Charges that repeat are listed but never added to the total. Type the monthly rent and months turn into money. Where a clause violates regional law, a dedicated alert cites the specific statute (such as England's Tenant Fees Act 2019 or New York's General Obligations Law 7-108).

### Physical ink document viewer
Renders the complete lease in Source Serif with generous line height and margin clause numbers. Flagged clauses feature irregular marker highlighter strokes drawn beneath the text. Tapping any mark expands the finding details. A right-hand jump rail shows marker locations across the entire document for quick navigation.

### Negotiation counter-proposal drafter
Finding a problem is only half the work; the tenant still needs to reply. Redline drafts the reply from the clauses you tick, with fair wording proposed for each:
- **Email or WhatsApp:** a letter with a subject line, or a short chat message sent straight to WhatsApp.
- **Friendly or firm:** the firm tone cites the law for the place you chose, where a clause is void there.

### Side-by-side lease comparison
Every lease you scan is kept on the phone (the last 12), and the recent ones are listed on the home screen. Renter Pro puts any two side by side: a verdict naming the safer lease and why, the score, serious and flagged clauses and rent at stake with the better side marked, and paired bars for the four categories.

## Verify the monetization in 60 seconds

- **Entitlement identifier:** `full_report`, defined in `Billing.kt`.
- **Honest price presentation:** The button price comes dynamically from the RevenueCat offering, never hardcoded strings. If multiple packages exist, the label shows "From $X" only when tiers differ, and "paid once" only when every plan is non-recurring.
- **No purchase ambush:** The price appears on the home screen before the user scans, and on the bottom bar of the report.
- **Tiered paywall:** plans are read from the offering by package type. A custom package whose identifier contains `pass` is the single-lease pass, kept on the phone against a fingerprint of that lease. Lifetime, annual and monthly packages are Renter Pro, which opens every report and the comparison. A free trial on a subscription changes the button to "Start 7 days free" (for a seven-day trial), and the terms line says what is charged after it.
- **Honest button state:** The purchase button stays disabled until `Billing.known` confirms entitlement state from `CustomerInfo`. A paid report is never shown and then abruptly locked, and users are never prompted to buy what they already own.
- **Offline resilience:** If launched without network, the paywall does not crash or trap the user. When a network connection returns, RevenueCat re-fetches the offering, and an `UpdatedCustomerInfoListener` synchronizes entitlement state without requiring an app restart.
- **Account-free restoration:** RevenueCat gets an app user ID derived from a salted SHA-256 hash of `ANDROID_ID`, so after a reinstall Renter Pro is back on the first launch without tapping restore. A single-lease pass is remembered on the phone, so it does not survive an uninstall.
- **Zero API keys committed:** `BuildConfig.REVENUECAT_API_KEY` is loaded from `local.properties`. With no key the app still builds, runs and scans, with the paywall locked.

## Why rules instead of embeddings

Sentence embeddings were tested and rejected early in development.

Thirty real lease clauses (twenty costly and ten benign) were labelled before the rules were written. Two acceptance targets were set in advance: correct category identification on at least fourteen of twenty, and true-positive margins at least three times the benign margin.

| Approach | Costly clauses caught | Benign clauses left alone |
|---|---|---|
| Embeddings (Universal Sentence Encoder) | 8 / 20 | 2 of 10 scored above median correct match |
| Deterministic rules | **20 / 20** | **10 / 10** |

The failure mode with embeddings was not just low recall; it was high false-positive confidence on harmless clauses. A clause stating that the tenant pays their own electricity scored higher than sixteen truly dangerous clauses.

Rules provide what probabilistic models cannot:
1. Exact sentence citations behind every flag.
2. Identical, deterministic output on every run.
3. Zero network latency and zero inference cost per scan.

## Regional statutory support

The app checks local tenant protections for six jurisdictions, shifting thresholds and citing legislation directly:

| Place | Deposit cap | Deposit back within | Late fees | Cited as |
|---|---|---|---|---|
| California | 1 month's rent (2 for a landlord with no more than two properties and four homes) | 21 days, itemised | Only a fair estimate of the landlord's cost | Civil Code 1950.5, 1671 |
| New York | 1 month's rent, counting rent paid in advance | 14 days | Lesser of $50 or 5% of rent, once rent is 5 days late | General Obligations Law 7-108, Real Property Law 238-a |
| Massachusetts | 1 month's rent | 30 days, itemised and sworn | None until rent is 30 days overdue | General Laws ch. 186, s. 15B |
| Texas | No cap | 30 days, after a forwarding address in writing | Presumed reasonable up to 12% (four homes or fewer) or 10%, once rent is 2 full days late | Property Code 92.103, 92.019 |
| England | 5 weeks' rent (6 where the year's rent is £50,000 or more) | 10 days from agreeing the amount | No late fee; interest only, on rent 14 days late, at most 3% above base rate | Tenant Fees Act 2019 |
| India | None in most states; the Model Tenancy Act 2021 proposes 2 months as a model | Not covered | Not covered | Model Tenancy Act 2021 |

## Build and run

Requires Android SDK 36 and JDK 17 or newer.

```bash
git clone https://github.com/Kesav2k04/redline.git
cd redline
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

To generate Roborazzi screenshot verification tests:

```bash
./gradlew :app:testDebugUnitTest -Pshots
```

## Accessibility

- **Unified screen reader announcements:** Every finding card reads as a single cohesive statement including the issue, consequence, and quoted clause text.
- **Accessible touch targets:** All interactive surfaces maintain at least 48dp touch targets.
- **Full scale support:** Layouts adapt to 200% system font scaling without clipping text or primary actions.
- **Reduced motion support:** Motion animations respect Android's system `ANIMATOR_DURATION_SCALE` setting, skipping transitions when animations are disabled.

## Legal notice

Redline is a contract inspection tool that highlights clauses based on defined rules and public statutes. It does not provide legal advice. Tenants should review the highlighted text and consult qualified legal counsel for dispute resolution.

## Licenses

- Redline application code: MIT License ([`LICENSE`](LICENSE)).
- Geist and Geist Mono typefaces: SIL Open Font License 1.1 ([`licenses/Geist-OFL.txt`](licenses/Geist-OFL.txt)).
- Source Serif 4 typeface: SIL Open Font License 1.1 ([`licenses/SourceSerif4-OFL.txt`](licenses/SourceSerif4-OFL.txt)).
- Solar duotone icons: Creative Commons Attribution 4.0 International ([`licenses/Solar-CC-BY-4.0.txt`](licenses/Solar-CC-BY-4.0.txt)), Copyright 480 Design.
