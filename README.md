# Redline

Reads a rental lease and points at the clauses that could cost you money.

Built by a student for the Next Gen Award at RevenueCat Shipaton 2026.

Everything that touches the lease runs on the device. No account and no upload. Open the
PDF your landlord sent, photograph a paper copy, or paste the text: reading, OCR and the
scan all happen on the phone. The only network traffic is RevenueCat's, to show the price
and take the payment, and it never carries a word of the lease.

<p>
  <img src="docs/start.png" alt="Start screen. The promise, then three ways in: open a PDF, photograph the pages, or paste the text, with a sample lease to try." width="31%">
  <img src="docs/locked.png" alt="Report. Eleven of sixteen clauses flagged; the late fee clause is free in full, with what to ask for, and the other ten open with one payment." width="31%">
  <img src="docs/unlocked.png" alt="The report after purchase. Every clause in full, each with what to ask for, and a button to ask the landlord for the changes." width="31%">
</p>

The scan always runs to completion and the count is always honest. The first flagged
clause is free, in full, with the sentence it came from and what to ask for instead.
What the purchase buys is the other ten clauses and the eighteen problems inside them,
the change to ask for on each, and a letter to the landlord that asks for all of them.

## Why it works the way it does

The obvious build is on-device sentence embeddings: write out a bank of "costly clause"
patterns, embed the lease, rank by cosine similarity. That was the plan.

It was given a test it could fail, and it failed it.

Thirty real lease clauses, twenty costly and ten benign, were labelled and committed
**before** the archetypes existed, so the archetypes could not be shaped around the
answers. Two pass conditions were fixed in advance: top-1 category correct on at least
fourteen of twenty, and a true-positive margin at least three times the benign margin.

| | Embeddings | Rules |
|---|---|---|
| Costly clauses identified | **8 / 20** | **20 / 20** |
| Benign clauses left alone | 3 confident false flags | **10 / 10** |

The interesting part is not that it scored badly. It is *how*. A benign sentence about
paying your own electricity bill produced the second-highest confidence score in the
entire set, higher than fourteen genuinely costly clauses. The failure mode was not
missing bad clauses; it was confidently flagging harmless ones, which is worse than
saying nothing when the reader is trying to decide whether to sign.

So Redline extracts instead of guessing. It finds the actual number, checks it against a
stated limit, and shows you the clause it came from. Every flag can be argued with.

Full working, raw output and reproduction steps: [`eval/README.md`](eval/README.md).

## What it catches

Thirty-eight rules across late fees, deposits, repairs, lock-in periods, notice
requirements, rent escalation, entry rights, occupancy limits, automatic renewal,
deemed service, pass-through charges, legal costs, liability waivers and restoration
costs. The first twenty-seven were written against Indian leases. Eleven more came from
a set of US and English clauses, which took the scanner from 6 to 18 of 26 costly US
clauses and from 3 to 13 of 19 English ones, with fewer false flags on both, and left
every Indian result where it was. That set shaped the new rules, so it is a regression
check rather than a measure of how far they reach.

Each finding names the figure it found. "Deposit equal to ten months rent", not
"deposit risk detected".

### How well, measured

| Set | Costly caught | Benign left alone |
|---|---|---|
| Frozen corpus, 30 clauses | 20 / 20 | 10 / 10 |
| Held out, written after the rules | 4 / 5 | 5 / 5 |

The held-out set uses deliberately different language (lessor and lessee, "demised
premises", "surcharge") and none of it was available while the rules were written. The
one miss is documented rather than patched, because editing a rule so a held-out clause
passes turns it into a training clause.

This is why the app says **"no rule matched"** and never "this lease is clean". Rules
catch what they were written to catch, and when they miss they say nothing.

### What those numbers were quietly hiding

20 of 20 and 10 of 10 was believed for longer than it deserved. The benign half of that
corpus was ten benign *lease* clauses. Nothing in it asked what the scanner does with
text that is not a lease, which is close to the first thing a stranger with the app open
will try.

It did something embarrassing. Four off-topic documents were run through it and every one
tripped a rule:

| Pasted in | What it said |
|---|---|
| A recipe | "The landlord alone decides what to deduct" |
| A privacy policy | "Staying on counts as agreeing" |
| An employment offer | "Leaving early still costs you the rest of the term" |
| A news story about parking charges | "Another occupant raises the rent by 10 percent" |

Several rules match ordinary commercial English. "At its sole discretion" is in every
employment offer written; "continued use constitutes acceptance" is in every privacy
policy.

The rules were not narrowed, because they are right about leases and bending them to
dodge a recipe would cost real catches. The document is checked instead, once, in
[`LeaseCheck.kt`](app/src/main/java/dev/kesav/redline/LeaseCheck.kt), which asks for two
distinct words that only tenancies use. That is loose enough to accept a single clause
shared in from another app and tight enough to reject all four documents above.

One rule was later narrowed for a reason of its own. US leases say "sole discretion" about
pets, sublets and alterations, where "the landlord alone decides what to deduct" is simply
false, so the rule now needs a deduction or the deposit in the same clause. The recipe
stopped tripping it as a side effect; the privacy policy and the job offer still trip
others, so the check still earns its place.

The first version of that list was too narrow in the other direction. An Indian leave and
licence deed names a licensor and a licensee and never says landlord, and 9 of 24 real
clauses in [`OnTopicTest`](app/src/test/java/dev/kesav/redline/OnTopicTest.kt) were told
they were not a lease. Now 0 of 24 are. Licensor, licensee and licence count as one word
rather than three, because "the Licensor grants the Licensee a licence to use the
Software" would otherwise pass as a tenancy, and a test holds that line. Five clauses
still fail, each with its reason written beside it.

When the check fails the app says so, shows what matched anyway so the claim can be
checked, and **does not offer to sell anything**. `OffTopicTest` pins all of it, including
an assertion that the privacy policy and the job offer still trip rules, so the day the check stops
earning its place the test says so rather than going quietly green.

The second thing the corpus hid was inside real leases, and it was worse, because a lease
passes the check above and the false headline then sits behind the paywall. No trigger
word had a word boundary, and six of the seven rules that read a number took the first
one in the clause. So "the rent shall be escalated by ten percent" came out as *Late
payment penalty of 10 percent*: "late" sits inside "escalated", and the only percentage
in the clause was read as the fee. "Residential" became a second occupant. An
eleven-month term became a notice period.

Every trigger now starts on a word boundary, and every rule that reads a number takes the
one nearest a required anchor word, within a reach set per rule. A late fee has to sit
within forty characters of *charge*, *fee*, *penalty* or *interest*; a notice period
within forty of *notice*. [`AdversarialTest`](app/src/test/java/dev/kesav/redline/AdversarialTest.kt)
holds the clauses built to break it, including one with two percentages where the late
fee must come out as 4 and not 10, beside six plain clauses that must still fire. The
frozen corpus scores the same 20 of 20 and 10 of 10 afterwards, which says less about
the fix than it does about the corpus.

## Verify the monetization in 60 seconds

- Entitlement identifier: `full_report`, declared once in
  [`Billing.kt`](app/src/main/java/dev/kesav/redline/Billing.kt) and nowhere else.
- Entitlement is read from `CustomerInfo` in `Billing.refresh()`. The report is drawn
  locked until that answer arrives, and the buy button stays disabled until then
  (`Billing.known`), so a paid result is **never shown and then withdrawn**, and nobody
  is offered something they already own.
- The price on the button comes from the offering, not from a string in the code.
- Cancelled purchases are not treated as errors.
- The button sells a named package type, never `availablePackages.first()`. Reordering the
  offering in the dashboard, or a product failing to resolve and dropping out, would
  otherwise slide a subscription into that slot and quietly offer a recurring charge to
  somebody reading one lease. `chooseOffer` picks `LIFETIME` by type and has no fallback,
  because selling the wrong thing is worse than selling nothing.
- `Billing.restore()` implements restore on reinstall, and it actually works after one.
  There are no accounts here, so the purchase is keyed to a SHA-256 hash of `ANDROID_ID`,
  which is scoped to the signing key and outlives the app's own storage. Automatic device
  identifier collection is off, so what reaches RevenueCat is stable without being a device
  identifier. `PurchaseIdTest` pins that the hash stays stable, because nothing else here
  would notice if it stopped.
- What the purchase buys is durable. `Report.letter` writes the landlord a request for
  each change, and `Report.build` turns the findings into plain text for anyone else, both
  handed to `ACTION_SEND`, so the lease arrives by share and the argument leaves the same
  way. The full report also goes as a PDF, written to the app's own cache under one fixed
  name that each send overwrites, and shared through a `FileProvider`. Finding eleven costly clauses
  is only half the job: the tenant still has to raise them with a landlord, and retyping
  nineteen findings into a message is where that stops happening.
- No key in the repository. `BuildConfig.REVENUECAT_API_KEY` is read from
  `local.properties`, which is not committed. With no key the app still builds, runs and
  scans, with the paywall locked.

### What was actually run

On the release-signed APK, not a debug build. That distinction matters here: `ANDROID_ID`
is scoped to the signing key, so debug and release are two different customers and a
result from one proves nothing about the other.

| Step | Result |
|---|---|
| Offering resolves, live price on the button | yes |
| Purchase declined at the store | stays locked, button re-enables, reason shown |
| Purchase completed | every finding reveals, call to action removed |
| Uninstall, reinstall, scan again | unlocked with no tap and no second purchase |

The last row is the one worth reading. A full uninstall takes every local file with it,
so nothing but the derived app user id carries the purchase across, and the report came
back on its own.

The paywall sits **after** the scan. The scan always completes and the count is always
honest; what you pay for is which clauses and why. Charging before the scan would be
charging for something the reader has not yet been given a reason to want.

Three things are given away on purpose, and each one costs a sale in the short run:

- **The count, in full.** "11 of 16 clauses could cost you money" is the finding. Hiding
  the number would raise conversion and would also make the app worthless to anyone who
  declines, which is most people.
- **The whole checklist.** "See all 38 checks" opens every rule, grouped, before
  any money changes hands. The counts are derived from the rule table in
  [`Rules.kt`](app/src/main/java/dev/kesav/redline/Rules.kt), so the screen cannot
  advertise a check the scanner does not run, and `TopicsTest` fails the build if the
  two ever drift. A paywall in front of an unexplained judgement is the thing a reader
  is right to distrust.
- **The price, before the work.** It is on the first screen, read from the offering,
  next to the line saying the text never leaves the phone. Learning the cost only after
  reading a lease into the field is the shape of an ambush even when the number is small.

What is left to sell is the part that took the work: which clause, what it says, and why
it costs money. One payment, no subscription, because a tenant signs a lease roughly
once a year and billing them monthly for that would be the actual dark pattern.

## Build

Android SDK with platform 36, and a JDK 17 or newer.

```
git clone https://github.com/Kesav2k04/redline
cd redline
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The APK lands in `app/build/outputs/apk/debug/`. Nothing else is needed: no keys, no
services to sign up for, and no `local.properties` entries beyond `sdk.dir`. Opening the
folder in Android Studio writes that line for you; from a bare terminal, an `ANDROID_HOME`
pointing at the SDK does the same job and the file can stay absent.

The release APK is about 42 MB. Most of that is the on-device OCR model, shipped for three
processor types, and code that R8 would normally shrink: the release build stays
debuggable because RevenueCat's Test Store requires it.

To exercise the purchase flow, add your own key:

```
revenuecat.apiKey=goog_yourkeyhere
```

## How the text gets in

A lease usually arrives as a PDF or on paper, so those come first.

- **Open the PDF.** On Android 15 and later the text layer is read directly with
  `PdfRenderer`. A scanned PDF, or any PDF on an older phone, is rendered page by page and
  read with ML Kit's bundled text recognition, which runs on the phone and needs no
  download. Up to 30 pages. The file is copied to the app's cache for the renderer and
  deleted as soon as reading finishes.
- **Photograph it**, a page at a time. The camera app hands the picture back through a
  `FileProvider`, so Redline asks for no camera permission.
- **Open with Redline** or **share to Redline** from Gmail, WhatsApp or Files, for a PDF,
  an image or plain text (`ACTION_VIEW`, `ACTION_SEND`).
- Paste it, or select text anywhere in Android and pick Redline (`ACTION_PROCESS_TEXT`).
- Or tap "Try it on a sample lease".

Imported pages are rebuilt into paragraphs before the scan: page numbers and running
headers are dropped, wrapped lines are rejoined, and headings and numbered clauses start
new paragraphs. A test holds the PDF path to the pasted path, so the same lease gives the
same findings however it arrived.

## What it asks for

A finding that stops at "this is bad" leaves the reader with a problem and no next move.
Every rule carries the change to ask for: "a deposit no larger than the local legal cap",
"at least 24 hours written notice before any entry, except in an emergency". It appears
on the card under the reason.

After purchase, **Ask the landlord for these changes** drafts a short letter from those
asks, grouped by clause number, in the first person of someone who still wants the flat.
It carries no verdicts and no quotes of the scanner's reasons, because the landlord wrote
the lease and does not need to be told it is unfair. It leaves through the share sheet,
so the app never holds an address or a copy. The full report, with every clause quoted
and every reason, still goes to a parent or an adviser from the same screen.

## Splitting a lease into clauses

Harder than it looks, which is why it has its own tests. Lease text arrives hard wrapped
at an arbitrary column, with words broken across lines by a hyphen, and with the real
structure carried by numbering rather than by blank lines.

[`ClauseSplitter`](app/src/main/java/dev/kesav/redline/ClauseSplitter.kt) rejoins wrapped
lines, repairs hyphenated breaks, treats a new numbered item as a clause boundary even
with no blank line before it, and only then falls back to sentence boundaries for blocks
still too long to be one clause.

[`Numbers`](app/src/main/java/dev/kesav/redline/Numbers.kt) exists because leases spell
their quantities: "ten percent", "ninety days", "two thousand rupees". A rule cannot
compare anything until those are integers.

One bug worth naming, because the test that pins it is more interesting than the fix. On
*"six months notice, failing which three months rent is payable and adjusted against the
deposit"*, the deposit rule read the first months figure it found and announced a
six-month deposit that did not exist. Quantities now have to sit beside the thing they
describe.

## Accessibility

Not a checklist item here, because the people most likely to be handed a bad lease are
not always the people best served by an app.

- Every finding card is one merged announcement rather than five fragments, and it
  includes the clause text itself. Reading out "costly risk, deposit equal to ten months
  rent" and then withholding the sentence would hand a screen reader user the headline
  and keep the evidence.
- A locked finding announces that it is locked and why, so the severity and the count
  are available without paying, exactly as they are on screen.
- Severity carries a word, "Serious" or "Worth checking", not only a colour.
- Touch targets are at least 48dp. The editor scrolls and lifts above the keyboard, so
  the Scan button is reachable at 200% font scale.
- The reveal animation reads `ANIMATOR_DURATION_SCALE` and does nothing when animations
  are turned off device-wide.

## What this is not

Not legal advice. It is pattern matching over contract text, it was written against
Indian residential leases and extended with US and English clauses, and it will miss
things. Read the clause it shows you.

## Licence

MIT. See [`LICENSE`](LICENSE).
