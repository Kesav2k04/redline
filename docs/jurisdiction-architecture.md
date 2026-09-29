# Jurisdiction architecture

How Redline applies tenancy law today, and the specification for growing it from six places to twenty or more without giving up the three properties the product rests on: a scan runs on the phone, the same lease always gets the same answer, and every citation the app shows is one a person read at its source.

Part 1 describes the code as it stands at commit `b63823a`. Parts 2 to 5 are a design that has not been built. Part 3 lists the law for the first eight new markets, each figure with its section and source.

## Contents

1. [The model today](#1-the-model-today)
2. [The jurisdiction registry](#2-the-jurisdiction-registry)
3. [Eight new markets](#3-eight-new-markets)
4. [Why rule tables on the phone](#4-why-rule-tables-on-the-phone)
5. [Rollout and tests](#5-rollout-and-tests)

## 1. The model today

![Scan pipeline](architecture-pipeline.svg)

A lease reaches the rules as a list of clauses, cut at its numbered headings by `ClauseSplitter`. `LeaseCheck` separately judges whether the text is a lease at all, which decides whether the report can be sold, not whether it is scanned. From there three files decide what the reader sees.

### 1.1 Rules: `Rules.kt`

There are 38 rules. Each is a private `Pattern`:

| Field | Meaning |
|---|---|
| `id` | Stable rule id, used as the key everywhere else (`deposit-size`, `late-fee`) |
| `topic` | Heading in the "What it looks for" disclosure, one of nine |
| `severity` | `HIGH` or `MEDIUM` |
| `all` / `none` | Regexes that must all match, and regexes that must not, on the lowercased clause |
| `unit`, `atLeast`, `below` | For a quantity rule: the unit word, and the line the number must cross |
| `near`, `reach` | The anchor the number must sit beside, and how many characters away it may be |
| `headline`, `reason`, `ask` | The words on the card; `ask` reads after "Ask for" |

`Pattern.check` is a pure function of the clause text. A rule fires when every `all` regex matches and no `none` regex does. A quantity rule then takes the number nearest its anchor, within `reach`, and fires only if that number is at least `atLeast` or under `below`. A quantity rule whose anchor is missing does not fire, which is what stopped "rent escalated by ten percent" from being reported as a late fee.

The 38 rules by topic and severity (24 `HIGH`, 14 `MEDIUM`):

| Topic | Rules |
|---|---|
| What you pay for | 9 |
| Who decides | 6 |
| Access to your home | 4 |
| Leaving early | 4 |
| Your deposit | 3 |
| Paying late | 3 |
| Rent going up | 3 |
| Renewal and notices | 3 |
| Moving out | 3 |

Nine of them are quantity rules. These are the lines a place can move:

| Rule | Unit | Fires when | Anchor |
|---|---|---|---|
| `late-fee` | percent | at least 3 | yes |
| `interest-rate` | percent | at least 12 | yes |
| `deposit-size` | months | at least 4 | `deposit` |
| `deposit-refund-delay` | days | at least 31 | yes |
| `long-notice` | months | at least 3 | yes |
| `rent-escalation` | percent | at least 6 | yes |
| `occupant-surcharge` | percent | at least 5 | yes |
| `rent-in-advance` | months | at least 2 | yes |
| `short-entry-notice` | hours | under 24 | yes |

The rules themselves carry no place. `uk-fee` and `section-21` are English rules in practice only because the words they need are ones English leases use.

### 1.2 Places: `Places.kt`

There is no `Jurisdiction.kt`. The place is an enum of six, chosen once by the reader and kept on the phone:

```kotlin
enum class Place(val label: String) {
    CALIFORNIA("California"), NEW_YORK("New York"), MASSACHUSETTS("Massachusetts"),
    TEXAS("Texas"), ENGLAND("England"), INDIA("India");
}

internal class Limits(
    val floors: Map<String, Int> = emptyMap(),   // rule id -> replacement atLeast
    val reasons: Map<String, String> = emptyMap(), // rule id -> reason naming the statute
    val asks: Map<String, String> = emptyMap(),    // rule id -> ask naming the figure
)
```

`Scanner.scan(clauses, place)` swaps each rule's `atLeast` for the place's floor, runs the rules, then replaces the reason and the ask on every finding the place has words for. Rules the place is silent on keep their general wording. What each place overrides today:

| Place | Floors | Reasons | Asks | Void entries |
|---|---|---|---|---|
| California | `deposit-size` 2, `deposit-refund-delay` 22 | 3 | 5 | 2 |
| New York | `deposit-size` 2, `deposit-refund-delay` 15 | 3 | 3 | 2 |
| Massachusetts | `deposit-size` 2 | 4 | 4 | 2 |
| Texas | `late-fee` 11 | 3 | 3 | 0 |
| England | `deposit-size` 2 | 3 | 3 | 4 |
| India | none | 1 | 1 | 0 |

Every figure was read at its source on 24 Sep 2026, and the working is kept outside the repository. The file's own rule is the right one: where a place's law is silent, or a figure could not be confirmed, the general wording stays, because a limit the app has not checked is worse than none.

The place is persisted by its enum name, both in the app's preferences (`KEY_PLACE`) and in each saved scan in `leases.json`. Those six names are therefore permanent ids.

### 1.3 Void clauses: `Insight.kt`

A second table, `Insight.VOID`, maps a place and a rule id to the statute that already overrides the clause and one sentence saying how. The report shows these as clauses with no effect under local law. It holds 10 entries: two each for California, New York and Massachusetts, and four for England, including `section-21` under the Renters' Rights Act 2025.

### 1.4 Scoring: `Insight.kt`

Findings are grouped by clause, not by rule, so one paragraph that trips four rules counts once. A clause with any `HIGH` finding weighs 10 and a clause with only `MEDIUM` findings weighs 4. The score is

```
score = round(100 * (1 - exp(-weight / 40)))   clamped to 0..100
```

so one serious clause scores 22, three score 53 and five score 71. The curve never reaches 100, and each further clause adds less than the one before. The bands are **Low risk** under 30, **Caution** under 60 and **Toxic clauses** from 60.

Each rule id belongs to one of four categories, and each category gets its own score on the same curve with a scale of 20:

| Category | Rules |
|---|---|
| Financial exposure | 19 |
| Termination traps | 9 |
| Maintenance shifting | 6 |
| Privacy & entry | 4 |

The weights and the curve do not depend on the place. A 53 means the same thing in Boston and in London, and that property should survive the expansion.

### 1.5 What breaks at twenty places

1. **Two tables for one place.** A place's law lives in `Places.LIMITS` and again in `Insight.VOID`, with the citation typed twice. At six places a reviewer can hold both in their head. At twenty they will drift.
2. **A place can only lower a floor.** It cannot set a `below` (an entry notice shorter than its law allows), cannot add a rule of its own, and cannot switch a rule off.
3. **No weeks, no fractions.** `Numbers.Quantity.value` is an `Int` and there is no weeks unit. England's five-week cap is approximated as "two months or more", so a six-week deposit, which is over the English cap, is not flagged. New South Wales and New Zealand also cap a bond in weeks.
4. **One language.** `LeaseCheck` stems, the abbreviation list in `ClauseSplitter`, the number words in `Numbers`, the unit regexes and the currency symbols (`$`, `£`, `₹`, `rs`, `inr`, `usd`, `gbp`) are all English. A German lease would still be scanned, but the English triggers would find nothing in it, and `LeaseCheck` would call it not a lease, so it could never be sold. There is no `€`.
5. **Law is not always national.** Tenancy law in Australia and Canada is set by each state or province, German rent caps depend on a state ordinance, and Spain has regional rules on top of national ones. A flat list of countries cannot say that.
6. **Some triggers are one word.** `deposit-size` needs the English word "deposit". A lease in Sydney or Auckland says "bond".

## 2. The jurisdiction registry

One record per jurisdiction holds everything the app knows about that place's law, and nothing about a place lives anywhere else. The records are Kotlin source compiled into the APK, not data downloaded at run time, so a typo in a regex fails the build or a unit test, and a scan never waits on a network.

### 2.1 The contract

```kotlin
package dev.kesav.redline.law

/** One place whose tenancy law the scanner applies. Every figure names its statute. */
data class Jurisdiction(
    /** Persisted. The six existing places keep their enum names: "CALIFORNIA" ... "INDIA". */
    val id: String,
    val label: String,
    /** ISO 3166-1 alpha-2, "DE". */
    val country: String,
    /** ISO 3166-2 where the law is set below the country, "AU-NSW", "CA-ON". */
    val region: String? = null,
    /** BCP 47 tags of the languages its leases are written in, "de", "en". */
    val languages: List<String>,
    /** ISO 4217, "EUR". */
    val currency: String,
    /** ISO date on which every figure below was last read at its source. */
    val checked: String,
    val statutes: Map<String, Statute>,
    val limits: Limits,
    /** Rule id to the words and the void entry this place has for it. */
    val overrides: Map<String, Override> = emptyMap(),
    /** Rules that exist only here, for clauses no other place's leases contain. */
    val localRules: List<RuleSpec> = emptyList(),
)

data class Statute(val citation: String, val url: String)

/**
 * The quantitative constraints. Null means the record has no sourced figure, either because
 * the law sets none or because nobody has read the section yet; either way the general
 * wording stays, and the record's comment says which.
 */
data class Limits(
    val deposit: DepositCap? = null,
    /** Security the law allows beside the deposit: a New Zealand pet bond, a Spanish garantía adicional. */
    val extraSecurity: List<Cap> = emptyList(),
    val depositReturn: Deadline? = null,
    val advanceRent: Cap? = null,
    val lateFee: LateFee? = null,
    val repair: RepairWindow? = null,
    /** One per purpose the law distinguishes: inspection, repairs, showings. */
    val entry: List<EntryNotice> = emptyList(),
)

data class Amount(val value: Double, val unit: RentUnit)
enum class RentUnit { WEEKS_OF_RENT, MONTHS_OF_RENT }

/** furnishedMax where the law caps a furnished let differently, as France does. */
data class DepositCap(
    val max: Amount,
    val furnishedMax: Amount? = null,
    val instalments: Int? = null,
    val statute: String,
)
data class Cap(val max: Amount, val statute: String, val name: String? = null)
data class Deadline(val days: Int, val from: String, val statute: String)
data class LateFee(
    val banned: Boolean = false,
    val maxPercent: Double? = null,
    val maxMoney: Long? = null,
    val graceDays: Int? = null,
    val statute: String,
)
data class RepairWindow(val urgentHours: Int? = null, val routineDays: Int? = null, val statute: String)
data class EntryNotice(val hours: Int, val purpose: String, val statute: String)

data class Override(
    /** The rule does not run here, because this place's law allows what it flags. */
    val off: Boolean = false,
    val reason: String? = null,
    val ask: String? = null,
    /** Statute key, when this place's law makes the clause void rather than unwise. */
    val voidUnder: String? = null,
    val voidWhy: String? = null,
)
```

Three choices in this contract matter more than the rest.

**Figures, not floors.** A place states its law ("a bond of at most four weeks' rent, section 159") and the engine derives each rule's line from it. Today a place states the rule's threshold directly ("`deposit-size` fires at 2"), which is how a one-month cap became "two or more months" and a five-week cap became the same thing.

**One record, one citation.** `Limits`, `Override` and `localRules` all point into the record's own `statutes` map by key. The reason text, the ask and the void entry cannot cite different sections for the same figure, because there is one place to write the section.

**A date on every record.** `checked` is shown in the app beside the citation ("Law checked 29 Sep 2026"). A reader can see how old the figure is, and a release can list every record older than a set age.

### 2.2 Rules derive their lines from the law

`Pattern` becomes a public `RuleSpec` with two additions: the `Category` it scores under (which retires the separate `CATEGORY` list in `Insight.kt` and its silent fallback to Financial exposure), and triggers per language. The quantity rules read their lines from the record:

| Rule | Reads | Fires when the clause states |
|---|---|---|
| `deposit-size` | `limits.deposit`, `limits.extraSecurity` | a deposit above `max` (or `furnishedMax` for a furnished let), compared in the unit of the cap, with each named extra security measured against its own cap |
| `deposit-refund-delay` | `limits.depositReturn` | a return period longer than `days` |
| `rent-in-advance` | `limits.advanceRent` | rent in advance above `max` |
| `late-fee` | `limits.lateFee` | any fee where `banned`; a percentage above `maxPercent`; a fee before `graceDays` |
| `short-entry-notice` | `limits.entry` | a notice shorter than the place allows for that purpose, or than its shortest notice when the clause names no purpose |
| `tenant-structural-repairs` | `limits.repair` | repairs shifted that the law keeps with the landlord (reason names the window) |

Where a record has no figure, the rule keeps its general line (four months, 31 days, 3 percent), exactly as it does today for a place with no floor. Comparisons change from "at least" to "above": with fractional quantities, a deposit of exactly the cap is lawful and must not be flagged.

Weeks and months convert at 52 / 12 weeks to the month, and the comparison happens in the cap's own unit. That matters in practice: a lease in Sydney asking for "one month's rent" as bond is asking for 4.33 weeks, which is over the four-week cap in New South Wales.

### 2.3 The language layer

Everything English-only moves behind one interface, with today's code as the English implementation, unchanged:

```kotlin
interface LeaseLanguage {
    val tag: String                            // "de"
    val leaseStems: List<String>               // LeaseCheck: Mietvertrag, Vermieter, Mieter ...
    val clauseHeading: Regex                   // ClauseSplitter: "§ 4", "Article 4", "Artículo 4"
    val units: Map<QuantityUnit, Regex>        // Monat(e), Monatsmiete(n), Woche(n), Tag(e), Stunde(n)
    val currency: Regex                        // "€", "EUR"
    fun quantities(text: String, unit: QuantityUnit): List<Quantity>   // number words, decimal comma
}
```

`Quantity.value` becomes a `Double` so that "1,5 Monatsmieten", "one and a half months" and "4.33 weeks" can be held. `RuleSpec` keeps its English `all`, `none` and `near` regexes as the `en` entry of a map from language tag to triggers, and each new language adds its own entry for each rule it covers. A rule with no entry for a language is not run on leases in that language, rather than run with English words that will never match.

OCR needs no change for the first eight markets. The Latin-script model bundled now (ML Kit `text-recognition` 16.0.1) lists German, French, Spanish and Dutch among its supported languages.

The reader still chooses the place, as now. The place fixes the candidate languages; where it has more than one, the language whose `leaseStems` match most often wins.

### 2.4 Layout and identity

```
dev/kesav/redline/law/Jurisdiction.kt        the types above
dev/kesav/redline/law/Jurisdictions.kt       the list, byId(), and the six legacy ids
dev/kesav/redline/law/places/California.kt   one file per jurisdiction
dev/kesav/redline/lang/English.kt            one file per language
```

`Jurisdictions.byId("CALIFORNIA")` returns the California record, so every stored preference and every saved scan keeps working with no migration. New ids follow the region code where the law is regional (`AU_NSW`, `AU_VIC`, `CA_ON`) and the country code where it is national (`DE`, `FR`, `ES`, `NL`, `IE`, `NZ`). An id, once shipped, is never renamed.

### 2.5 The engine after the change

`Scanner.scan(clauses, jurisdiction)` does the same four steps for every place:

1. Pick the lease language from the jurisdiction's `languages`.
2. Run every global rule that has triggers in that language and is not switched off by an `Override`, with lines derived from `limits`.
3. Run the jurisdiction's `localRules`.
4. Apply `overrides` to each finding: the reason, the ask, and the void entry, all citing the same statute key.

Scoring does not change. The weights, the curve, the bands and the four categories stay the same everywhere.

## 3. Eight new markets

The brief named eight markets. Australia counts as two, because tenancy law there is set by each state: New South Wales under the Residential Tenancies Act 2010 (NSW), Victoria under the Residential Tenancies Act 1997 (Vic). That makes nine records.

Every figure below was read in the consolidated legislation each government publishes, in versions current in September 2026, or, for the few German rules that rest on case law, in the judgment. Where a figure comes from a regulator's page rather than the Act, the row says so. A cell reading "no figure" means the law sets none, and the record leaves that limit null so the general wording stays. A cell reading "not yet checked" was outside this pass, and stays null until someone reads the section.

### 3.1 Where the brief was wrong or out of date

| Market | The brief said | The law says |
|---|---|---|
| Victoria | Governed by the Residential Tenancies Act 2010 (NSW), bond of four weeks, break lease formula | Governed by the Residential Tenancies Act 1997 (Vic). Bond of one month's rent (s 31), and no statutory break fee scale: VCAT sets compensation (s 211A) |
| New South Wales | Two days' entry notice | Two days is for repairs and maintenance (s 55(2)(b)). A general inspection needs seven days' written notice, at most four times in twelve months (s 55(2)(a)) |
| Ontario | Standard lease mandatory | Mandatory for most tenancies entered from 30 April 2018. Care homes, mobile home sites and rent-geared-to-income housing are excluded (O. Reg. 9/18). A refundable key or fob deposit is lawful (O. Reg. 516/06 s 17) |
| Ireland | Residential Tenancies Act 2004: Rent Pressure Zones, deposit of one month | The one-month deposit cap is s 19B, inserted by the Residential Tenancies (No. 2) Act 2021 for tenancies from 9 August 2021. The Rent Pressure Zone sections (ss 24A to 24C) were repealed on 1 March 2026 and replaced by a national cap in s 19: the lower of 2 percent a year and inflation |
| New Zealand | Bond of at most four weeks | Four weeks is the general bond (s 18). Since 1 December 2025 a landlord who allows a pet may also take a pet bond of up to two weeks (s 18AA), so a total of six weeks can be lawful |
| Germany | BGB § 535 (Schönheitsreparaturen) | § 535 puts upkeep on the landlord and does not mention decoration. Shifting it to the tenant is a question of standard-terms control under § 307 and case law: a form clause is void where the flat was handed over unrenovated with no fair compensation (BGH VIII ZR 185/14). The three deposit instalments in § 551 are the tenant's right, not the landlord's choice |
| France | Loi ALUR and Décret n° 2015-650 (encadrement des loyers) | Rent control now runs under art. 140 of the ELAN law (loi n° 2018-1021), and the 2015 decree was amended to refer to it. The ELAN scheme runs eight years from publication, so it lapses in late November 2026 unless it is extended |
| Spain | Rent update linked to the IPC | Rent is updated only if the lease says so (LAU art. 18.1). Where the parties agree an update but name no index, the default is the IGC, and the IPC is only a ceiling. For leases signed after 26 May 2023 the limit is the INE's IRAV index (additional provision 11, added by Ley 12/2023) |
| Netherlands | Burgerlijk Wetboek Boek 7 (Puntensystematiek) | The point system is in the Uitvoeringswet huurprijzen woonruimte and its decree, not in Book 7. The two-month deposit cap is BW 7:261b |

### 3.2 The limits, as the contract holds them

| Record | Deposit cap | Deposit back | Rent in advance | Late fee | Entry notice |
|---|---|---|---|---|---|
| `AU_NSW` | 4 weeks' rent (s 159(1)) | Held by the Rental Bond Board; paid out on claim (ss 163 to 168) | 2 weeks (s 33(2)) | Banned: a penalty for breach is a prohibited term (s 19(2)(d)) | 7 days for inspection, 2 days for repairs (s 55(2)) |
| `AU_VIC` | 1 month's rent (s 31(1)); no cap above a prescribed weekly rent, $900 per Consumer Affairs Victoria | Held by the Residential Tenancies Bond Authority; paid out on claim (s 411) | 1 month (s 40) | Banned (s 27B(1)(c)) | 7 days for inspection, 24 hours for repairs (s 85) |
| `CA_ON` | Rent deposit only: the lesser of one rent period and one month, applied to the last period (ss 105, 106) | Applied to the last month, with yearly interest at the guideline rate (s 106(6), (10)) | Within the rent deposit | Banned: no charge may be collected unless prescribed; only an NSF charge up to $20 is (s 134; O. Reg. 516/06 s 17) | 24 hours written, 8 a.m. to 8 p.m. (s 27) |
| `IE` | 1 month's rent (s 19B(1)) | "Promptly"; no figure (s 12(1)(d)) | 1 month (s 19B) | No figure found in the Act | No figure: a date and time agreed in advance (s 16(c)) |
| `NZ` | 4 weeks' general bond, plus up to 2 weeks' pet bond (ss 18, 18AA) | Held by Tenancy Services; a landlord must apply within 2 months (s 22A) | 2 weeks (s 23(1)) | No effect: a penalty for breach is void (s 32(1)) | 48 hours to 14 days for inspection, 24 hours for repairs (s 48(2)) |
| `DE` | 3 months' rent without operating costs, payable in 3 monthly instalments (BGB § 551(1), (2)) | No figure: a reasonable period set by case law (BGH VIII ZR 71/05) | Not yet checked | Any contractual penalty is void (§ 555); default interest is 5 points above the base rate (§ 288) | No figure: entry only for a concrete reason, after notice (BGH VIII ZR 289/13) |
| `FR` | 1 month's rent without charges; 2 months for a furnished let (loi 89-462 arts. 22, 25-6) | 1 month if the exit inventory matches the entry one, otherwise 2; a late return adds 10 percent of the monthly rent per month (art. 22) | Not yet checked; no deposit may be taken where rent is paid more than two months ahead (art. 22) | Penalty clauses and reminder charges are void (art. 4 i), p)) | No figure; viewings at most 2 hours on working days and never on public holidays (art. 4 a)) |
| `ES` | 1 month's fianza (LAU art. 36.1), plus an agreed extra guarantee of up to 2 months for leases of up to 5 years (art. 36.5) | No figure; legal interest runs from 1 month after the keys are returned (art. 36.4) | Not yet checked | No figure in the LAU; the court moderates a penalty (Código Civil arts. 1152, 1154), and a disproportionate one against a consumer is abusive (TRLGDCU art. 85.6) | No entry without consent or a court order (Constitución art. 18.2) |
| `NL` | 2 months' kale huur, the rent for use alone (BW 7:261b(2), 7:237(2)) | 14 days, or 30 where damage or arrears are deducted (7:261b(3), (4)) | Not yet checked | A penalty clause is allowed, and the court may reduce it (BW 6:91, 6:94) | No figure; the tenant must allow urgent works and viewings (BW 7:220, 7:223) |

Repairs carry no single number anywhere, so `RepairWindow` holds what the law actually fixes: New South Wales reimburses a tenant's urgent repair within 14 days, up to $1,000 (ss 62, 64); Victoria requires non-urgent repairs within 14 days of written notice (s 74) and reimburses urgent ones within 7 days up to a prescribed cap, $2,500 per Consumer Affairs Victoria (s 72). Ontario, Ireland and New Zealand set a duty to repair with no day count, which leaves `routineDays` null.

In the civil-law markets the question is which repairs may be moved to the tenant, not how fast the landlord must act. France puts the "réparations locatives" listed in Décret n° 87-712 on the tenant and voids a clause binding the tenant in advance to the landlord's own estimate (loi 89-462 arts. 7 d), 4 f)). Spain puts small repairs from ordinary wear on the tenant (LAU art. 21.4). The Netherlands puts the "kleine herstellingen" in the Besluit kleine herstellingen on the tenant, including whitewashing interior walls and painting interior woodwork, and that cannot be varied to the tenant's detriment (BW 7:240, 7:242). Germany allows decoration to be shifted only within the limits the courts have set. Euro caps for small repairs in Germany exist only in lower-court practice and are left out.

### 3.3 Each record in brief

**New South Wales (`AU_NSW`, `en`, AUD).** Residential Tenancies Act 2010, in force version of 21 September 2026. Needs one local rule, `break-fee-scale`: for a fixed term of three years or less the break fee is 4, 3, 2 or 1 weeks' rent as under 25, 50, 75 or at least 75 percent of the term has passed (s 107(4)), so a flat fee above that scale is flagged. Rent may rise once in twelve months on 60 days' notice (s 41). Anchors: "rental bond", "break fee", "rent in advance", "urgent repairs".

**Victoria (`AU_VIC`, `en`, AUD).** Residential Tenancies Act 1997, version 114 of 9 September 2026. Victorian leases say "renter" and "residential rental provider", not "tenant" and "landlord", so every English rule that anchors on those words needs the Victorian terms added. No break fee scale to encode; the `lock-in` and `liquidated-damages` rules keep their general wording with a reason citing s 211A. Anchors: "residential rental provider", "lease break fee", "reletting fee", "notice of intention to vacate". The $900 threshold and the $2,500 cap come from the regulator and must be read in the Residential Tenancies Regulations 2021 before they ship.

**Ontario (`CA_ON`, `en`, CAD).** Residential Tenancies Act, 2006, consolidated 21 September 2026. Needs `damage-deposit`: any security deposit other than last month's rent is barred (s 105(1)), with a refundable key deposit as the one exception. Lease terms inconsistent with the Act are void (s 4(1)), which makes most `Override.voidUnder` entries point at s 4 plus the specific section. The 2026 rent guideline is 2.1 percent. Anchors: "last month's rent", "rent deposit", "key deposit", "damage deposit". French-language Ontario leases can follow as a second language once `fr` exists for France.

**Ireland (`IE`, `en`, EUR).** Residential Tenancies Act 2004 as revised to 1 March 2026. Needs `rent-review-cap`: from 1 March 2026 a rent review may not raise rent by more than 2 percent a year or inflation, whichever is lower (s 19(3), (4)), and not more than once in twelve months (s 20). The late fee field stays null: no provision was found in the Act. Act 33/2026 amends ss 12, 19 and 22 but had not commenced on 17 September 2026, so this record must be re-read before it ships. Anchors: "security deposit", "rent in advance", "rent review".

**New Zealand (`NZ`, `en`, NZD).** Residential Tenancies Act 1986 as at 1 December 2025. The deposit rule must read "bond" and treat a pet bond as its own limit of two weeks, so a lease asking four weeks' bond and two weeks' pet bond is lawful and five weeks' bond alone is not. Key money is prohibited (s 17). Anchors: "bond", "pet bond", "rent in advance", "letting fee", "key money".

**Germany (`DE`, `de`, EUR).** BGB as published on gesetze-im-internet.de. Needs two local rules. `vertragsstrafe` flags any contractual penalty, void under § 555. `schoenheitsreparaturen` flags a form clause moving decoration to the tenant: rigid schedules ("alle drei Jahre") are void, and other shifts are a check the tenant should make, because the text cannot say whether the flat was handed over renovated. The Mietpreisbremse (at most 10 percent above the local comparative rent, § 556d, in areas named by a state ordinance until 31 December 2029) cannot be judged from a lease alone and is left to the reason text. A further reform ("Mietrecht II") had its first Bundestag reading on 9 July 2026 and must be checked before this record ships. Anchors: "Kaution", "Mietsicherheit", "Schönheitsreparaturen", "Kleinreparaturen", "Vertragsstrafe".

**France (`FR`, `fr`, EUR).** Loi n° 89-462 of 6 July 1989 as consolidated on Légifrance. The deposit rule needs the lease's furnished status ("meublé"), which is what `furnishedMax` is for. Article 4 is a list of clauses deemed unwritten, which maps almost line for line onto local rules with a `voidUnder`: penalties (4 i)), reminder charges (4 p)), the landlord's estimate (4 f)) and viewings beyond two hours or on public holidays (4 a)). Rent control lapses in late November 2026 unless extended, so the record carries no rent cap until that is settled. Anchors: "dépôt de garantie", "pénalités de retard", "réparations locatives", "frais de relance", "révision du loyer".

**Spain (`ES`, `es`, EUR).** Ley 29/1994 (LAU), BOE consolidated text last updated 25 May 2023. The fianza is fixed at one month rather than capped, and the extra guarantee is a separate `extraSecurity` cap of two months, so one month's fianza with two months' guarantee is lawful and with three is not. Needs `actualizacion-renta` for an update that exceeds the agreed index or appears where the lease sets none. Autonomous communities can require the fianza to be lodged with a regional body, and stressed-market zones add regional rent limits; those become regional records under `ES` later. Anchors: "fianza", "garantía adicional", "actualización de la renta", "pequeñas reparaciones".

**Netherlands (`NL`, `nl`, EUR).** Burgerlijk Wetboek Book 7 as in force from 1 July 2026. The deposit is measured against the kale huur, so the rule must separate the rent for use from the service costs a Dutch lease lists beside it. The global `mandatory-repaint` rule is switched off with `Override(off = true)`, because the decree puts interior whitewashing on the tenant, and flagging a lawful duty would teach the reader to distrust the flags that matter. Whether a rent is lawful depends on the point value of the dwelling under the Uitvoeringswet, which the lease does not state, so the app does not judge it. Anchors: "waarborgsom", "borg", "kale huur", "kleine herstellingen", "boete".

### 3.4 One record written out

New South Wales, as it would sit in `law/places/NewSouthWales.kt`:

```kotlin
val NEW_SOUTH_WALES = Jurisdiction(
    id = "AU_NSW",
    label = "New South Wales",
    country = "AU",
    region = "AU-NSW",
    languages = listOf("en"),
    currency = "AUD",
    checked = "2026-09-29",
    statutes = mapOf(
        "rta" to Statute(
            "Residential Tenancies Act 2010 (NSW)",
            "https://legislation.nsw.gov.au/view/whole/html/inforce/current/act-2010-042",
        ),
    ),
    limits = Limits(
        deposit = DepositCap(Amount(4.0, RentUnit.WEEKS_OF_RENT), statute = "rta"),      // s 159(1)
        advanceRent = Cap(Amount(2.0, RentUnit.WEEKS_OF_RENT), statute = "rta"),         // s 33(2)
        lateFee = LateFee(banned = true, statute = "rta"),                                // s 19(2)(d)
        repair = RepairWindow(routineDays = null, statute = "rta"),                       // ss 62 to 65
        entry = listOf(
            EntryNotice(hours = 7 * 24, purpose = "inspection", statute = "rta"),         // s 55(2)(a)
            EntryNotice(hours = 2 * 24, purpose = "repairs", statute = "rta"),            // s 55(2)(b)
        ),
    ),
    overrides = mapOf(
        "deposit-size" to Override(
            reason = "New South Wales caps a bond at four weeks' rent " +
                "(Residential Tenancies Act 2010, section 159).",
            ask = "a bond of no more than four weeks' rent, the New South Wales limit",
            voidUnder = "rta",
            voidWhy = "A bond above four weeks' rent cannot be required.",
        ),
    ),
    localRules = listOf(BREAK_FEE_SCALE),
)
```

`BREAK_FEE_SCALE` is the one rule New South Wales adds. Everything else in the record is data the global rules already know how to read.

## 4. Why rule tables on the phone

Rules compiled into the APK, with the law as data beside them, were chosen over sending the lease to a hosted language model. The reasons hold at twenty places as they do at six.

**No cost per scan.** A scan is regex matching and arithmetic in the app's own process. There is no server and no model call, so the thousandth scan costs what the first did: nothing. That is why the scan, the score and the count of flagged clauses can stay free with no limit on how many leases a reader checks.

**The lease never leaves the phone.** The rules, the law tables and the OCR model all ship inside the APK, and a scan works in airplane mode. A lease carries names, addresses, rent and sometimes identity numbers. With nothing to upload there is nothing to secure in transit, retain or leak. The app does make network calls, to RevenueCat for purchases and through ML Kit's own usage diagnostics, and neither carries the lease text. The README lists both.

**The same lease gets the same answer.** Same text, same place and same app version give the same findings, the same score and the same citations, on every phone and every run. That is what lets a test pin the behaviour: the 173 unit tests include clause-by-clause checks (`PlacesTest`, `UsUkTest`, `HeldOutTest`, `AdversarialTest`) that would fail if a rule's answer moved. A tenant who shows the report to a landlord and scans again the next day sees the same thing.

**The app cannot invent a citation.** Every statute the app names is a string in a registry record, with a URL and a date, written by a person who read the section. The engine can only select from those strings. A model asked for the law can return a section number that does not exist, or the right number with the wrong rule, and nothing in the output marks which. Here, a wrong citation is a bug in one record, visible in review and fixable in one line.

**What this costs.** Rules only find wording they were written for. On clauses they had never seen, today's rules caught 18 of 26 costly clauses from US leases and 13 of 19 from English ones, while leaving 25 of 29 and 20 of 20 benign clauses alone (`eval/README.md`). A paraphrase no rule anticipated is missed. The report says "Nothing matched" rather than calling such a lease clean, and each new market adds its own labelled clauses so the miss rate is measured rather than guessed.

## 5. Rollout and tests

### 5.1 Order of work

1. **Registry with no change in behaviour.** Move the six places into registry records, merge `Insight.VOID` into their overrides, keep the English rules as they are. Before merging, run every existing corpus (`clauses.tsv`, `heldout.tsv`, `intl.tsv`, `us-lease.txt`) under each of the six places and require the findings to be identical to the current build's. The existing 173 tests stay green.
2. **Weeks and fractions.** Make `Quantity.value` a `Double`, add the weeks unit and "bond" as a deposit trigger, and state England's cap as five weeks. The first visible change: a six-week deposit in England is flagged.
3. **English-language markets.** New South Wales, Victoria, Ontario, Ireland and New Zealand need records and a few local rules, but no new language.
4. **The language layer, then the civil-law markets.** Germany, France, Spain and the Netherlands each need a `LeaseLanguage`, the triggers for every rule they cover, and their records.

### 5.2 What a market needs before it ships

- A record in which every figure has a statute key, and every statute a citation, a URL and the date it was read. Where the law sets no figure, the field is null and the general wording stays.
- Labelled clauses in `eval/`, costly and benign, in the format `intl.tsv` already uses (id, label, category, text, jurisdiction, origin, source), with results written up beside the existing ones.
- The reason and ask texts for each figure, following the contract today's asks already follow: one noun phrase that reads after "Ask for", with no pronoun for the reader.

### 5.3 Tests the registry adds

| Test | Pins |
|---|---|
| Every limit's statute key exists in its record | No figure without a citation |
| Every statute URL is `https` and every record has a `checked` date | Every citation can be followed and dated |
| Every override names a rule that exists | What `PlacesTest` checks today, for every record |
| Every rule declares a category | The silent fallback to Financial exposure is gone |
| Every legacy id resolves to its record | Stored preferences and saved scans survive |
| Derived lines per jurisdiction | "A bond of one month's rent is over the cap in New South Wales and not in Ontario"-style cases, one or more per limit |
| Golden findings per language | A fixed lease per language produces a fixed list of findings |

---

*Written 29 Sep 2026 against commit `b63823a`. Part 1 was checked line by line against the code. Part 3's figures come with their sources; the working, including every figure that could not be confirmed, is kept in the project workshop.*
