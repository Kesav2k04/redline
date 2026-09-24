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

The scan always runs to completion and the count is honest. The first flagged clause is free in full, with the exact sentence it came from, the financial calculation, and what to ask for instead. The purchase opens the remaining clauses, the complete marked document viewer, the counter-proposal drafter, and side-by-side lease comparison.

## In five lines

- **What it does:** Reads a lease on the device (PDF, in-app CameraX scan, file share, or paste) and checks 38 rules across late fees, deposit locks, liability waivers, entry rights, and upkeep traps, quoting the exact clause and citing local tenant statutes for six jurisdictions.
- **Visceral UI:** Interactive 3D tilting lease hero with spring physics, 0-100 radial risk gauge, 4-category bento grid, physical marker ink document viewer with margin jump rail, counter-proposal drafter, and side-by-side comparison with butterfly bars.
- **Free:** The scan, 0-100 risk score, category bento, honest clause count, the first flagged clause in full, subjects of all locked clauses, and the complete 38-rule inspection catalog.
- **Paid, honest pricing:** Single Lease Pass ($1.99) for one lease, or Renter Pro ($4.99) for unlimited scans and side-by-side comparison, powered by RevenueCat under the `full_report` entitlement.
- **Privacy and offline guarantee:** Zero cloud processing for lease text. No accounts, no tracker SDKs, and purchases restore across uninstalls via a salted SHA-256 hash of the device signing scope.

## Product experience

### In-app camera scanner
Photograph paper leases directly inside the app with CameraX. A live ML Kit text recognition pipeline analyzes preview frames at 30 fps, drawing bounding boxes over recognized text and signalling when text is stable. Includes haptic shutter feedback, torch toggle, and multi-page capture queues.

### Interactive 3D lease hero
The home screen features a 3D contract card rendered in Compose graphics layers with spatial spring physics. Dragging the card tilts the page with realistic lighting glares and depth parallax, reacting to pointer position and snapping back on release.

### Radial risk gauge and bento grid
The report leads with a 0 to 100 risk gauge drawn with a sweep gradient:
- **Low Risk (0-29):** Standard terms with fair protections.
- **Caution (30-59):** Unbalanced clauses that warrant negotiation.
- **Toxic Clauses (60-100):** Serious financial exposure or void terms.

Under the gauge, the bento grid splits risk into four distinct pillars: Financial exposure, Privacy & entry, Termination traps, and Maintenance shifting.

### Stated financial exposure and void clauses
Redline calculates actual stated monetary exposure: deposit multiples, late fee caps, lock-in rent totals, and utility shift surcharges. Where a clause violates regional law, a dedicated alert cites the specific statute (such as England's Tenant Fees Act 2019 or New York Housing Stability and Tenant Protection Act).

### Physical ink document viewer
Renders the complete lease in Source Serif with generous line height and margin clause numbers. Flagged clauses feature irregular marker highlighter strokes drawn beneath the text. Tapping any mark expands the finding details. A right-hand jump rail shows marker locations across the entire document for quick navigation.

### Negotiation counter-proposal drafter
Finding a problem is only half the work; the tenant still needs to reply. Redline generates ready-to-send counter-proposals in two formats:
- **Formal Email:** Professional, structured negotiation letters citing statutory caps and asking for specific clause modifications.
- **Concise WhatsApp:** Direct, friendly text summaries built for landlords or brokers who communicate by chat.

### Side-by-side lease comparison
Tenants comparing two apartments can save scans into local storage and view them side by side. Redline plots paired butterfly risk bars across all four categories, highlights score differences, and marks which lease is safer for each issue.

## Verify the monetization in 60 seconds

- **Entitlement identifier:** `full_report`, defined in `Billing.kt`.
- **Honest price presentation:** The button price comes dynamically from the RevenueCat offering, never hardcoded strings. If multiple packages exist, the label shows "From $X" only when tiers differ, and "paid once" only when every plan is non-recurring.
- **No purchase ambush:** The price appears on the home screen before the user scans, and on the bottom bar of the report.
- **Tiered paywall:** Supports both a Single Lease Pass ($1.99 one-time consumable) and Renter Pro ($4.99 lifetime or recurring subscription). Single passes grant immediate access to the current lease report, while Renter Pro provides unlimited scans, side-by-side comparison, and negotiation drafting.
- **Honest button state:** The purchase button stays disabled until `Billing.known` confirms entitlement state from `CustomerInfo`. A paid report is never shown and then abruptly locked, and users are never prompted to buy what they already own.
- **Offline resilience:** If launched without network, the paywall does not crash or trap the user. When a network connection returns, RevenueCat re-fetches the offering, and an `UpdatedCustomerInfoListener` synchronizes entitlement state without requiring an app restart.
- **Account-free restoration:** Purchases are tied to an anonymous app user ID derived from a salted SHA-256 hash of the device signing scope (`ANDROID_ID`). A user who uninstalls and reinstalls the app recovers their purchase on the first launch without tapping restore.
- **Zero API keys committed:** `BuildConfig.REVENUECAT_API_KEY` is loaded from `local.properties`. When built without a key, the app compiles, runs, and completes scans with the paywall safely in sandbox mode.

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

| Jurisdiction | Security deposit cap | Deposit return deadline | Late fee restriction | Governing statute |
|---|---|---|---|---|
| California | 1 month rent (2 for small landlords) | 21 days | Fair estimate of actual costs | Cal. Civ. Code § 1950.5 |
| New York | 1 month rent | 14 days | Lesser of $50 or 5% of monthly rent | N.Y. Real Prop. Law § 238-a |
| Massachusetts | 1 month rent | 30 days | Prohibited until 30 days overdue | Mass. Gen. Laws ch. 186, § 15B |
| Texas | No statutory cap | 30 days | Presumed reasonable up to 10-12% | Tex. Prop. Code § 92.019 |
| England | 5 weeks rent | 10 days from agreement | Default fees capped; 3% above base rate | Tenant Fees Act 2019 |
| India | 2 months rent (Model Tenancy Act) | At handover | Presumed notice terms | Model Tenancy Act 2021 |

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
