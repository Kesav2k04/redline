# The measurement that chose how Redline works

Redline flags lease clauses that could cost you money. There were two candidate ways to
decide which clauses those are: rank each clause against a bank of written "costly clause"
patterns using on-device sentence embeddings, or extract the actual numbers and
obligations and check them against known limits.

Rather than argue about it, the semantic route was given a test it could fail, and it
failed it.

## The protocol

`clauses.tsv` holds 30 real lease clauses, 20 costly and 10 benign, each labelled with a
category. It was written and committed **before** `archetypes.tsv` existed, so the 40
archetypes could not be shaped around the answers. Git history shows the order.

Two conditions were fixed before the first run:

1. Top-1 category correct on at least **14 of the 20** costly clauses. Chance is 10% per
   clause, since there are ten categories, so 14 or more of 20 by luck has a probability
   of about 2 in 10 billion.
2. Median true-positive margin at least **3x** the 90th-percentile benign margin.

Margin means the gap between the winning archetype and the best archetype from a
different category. The runner-up overall would only measure how alike two archetypes in
the same category are, which is not the question.

## The result

Model: `universal_sentence_encoder.tflite`, 6,120,274 bytes, 100 dimensions, run through
MediaPipe TextEmbedder.

| | Raw cosine | Mean-centered |
|---|---|---|
| Top-1 category correct | **8 / 20** | **8 / 20** |
| Median true-positive margin | 0.0255 | 0.1446 |
| 90th percentile benign margin | 0.0223 | 0.1625 |
| Ratio (needs 3.00) | **1.14x** | **0.89x** |
| Verdict | FAIL | FAIL |

Mean-centering is the standard correction for the anisotropy that squeezes this model's
cosine range, and it worked on the range: raw spans 0.54 to 0.93, centered spans -0.55 to
0.73. It did not work on the decision. Benign margins grew at least as fast as true ones,
so the ratio got worse, not better.

## Why it failed, in the rows

- `c09`, a six month notice requirement, matched **security_deposit** by 0.0006. That is
  not a decision, it is a coin landing on its edge.
- `c12`, entry without notice, matched **security_deposit** by 0.0014.
- **repairs** behaved as a sink, absorbing early termination, rent increase, society
  charges and restoration clauses. A nearest-neighbour bank develops attractors.
- `b07` is a plain, harmless sentence about paying rent by bank transfer. Centered, it
  scored a margin of 0.1610, higher than fifteen of the twenty genuinely costly clauses.
  `b08`, about paying your own electricity bill, scored 0.1759, the highest benign margin
  in the set and higher than sixteen costly clauses.

That last pair is the important one. The failure is not that costly clauses get missed.
It is that harmless ones get flagged **confidently**, and a user reading a confident
wrong answer about their own lease has been actively misled.

## What was decided

Deterministic extraction. Pull the real quantities out of the text (the late fee
percentage, the notice period in days, the deposit as a multiple of rent, the lock-in
length) and compare them against stated limits, showing the clause and the reason on
screen. Every flag is then explainable and none of them is a guess.

Dropping the embedding route also removes a 127 MB native dependency from the app.

## Reproducing

```
uv venv --python 3.11 .venv
uv pip install --python .venv/Scripts/python.exe mediapipe numpy
curl -L -o eval/models/universal_sentence_encoder.tflite \
  https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/latest/universal_sentence_encoder.tflite
.venv/Scripts/python.exe eval/measure.py
```

`RESULTS.txt` is the unedited output of that command.

---

## What replaced it, and how well that works

Rules that extract the actual quantity and name it. There were 27 of them when this was
measured; there are 38 now, and the frozen corpus results below still hold.

### On the frozen corpus

| | Result |
|---|---|
| Costly clauses caught | **20 / 20** |
| Benign clauses left alone | **10 / 10** |

Compare that against 8 / 20 for the embedding route on identical text. This is a
regression fixture though, not a claim about unseen leases: the rules were written with
these clauses available, so passing here only proves they fire where intended.

### On a held-out set

`heldout.tsv` holds ten more clauses written **after** the rules were finished, in
deliberately different language: lessor and lessee rather than landlord and tenant,
"demised premises", "surcharge", "discharged by".

| | Result |
|---|---|
| Costly clauses caught | **4 / 5** |
| Benign clauses left alone | **5 / 5** |

### The known gap

`h01` is missed: *"A surcharge of five percent shall be levied where rent is received
after the tenth day of the month."* The late fee rule looks for one of unpaid, overdue,
default, delay or late, and that sentence uses none of them. It says the same thing by
naming a date instead.

It would take one word to fix and the fix is deliberately not being made, because
editing a rule so a held-out clause passes turns it into a training clause and the
number stops meaning anything. It is listed here instead.

That gap is also the honest shape of the whole approach. Rules catch what they were
written to catch. When they miss, they miss silently and say nothing, which is why the
app reports "no rule matched" rather than "this lease is clean".

## US and English clauses

`intl.tsv` holds 94 clauses from outside India: 55 from California, New York, Texas,
Florida, Illinois (mostly the Chicago ordinance) and the HUD model lease, and 39 from
England under the Tenant Fees Act 2019 and the Renters' Rights Act 2025. Each was
labelled against the law where it applies before the scanner saw it. Costly means
unlawful, of no effect there, or taking more than the local default.

| | Before the US and UK rules | Now |
|---|---|---|
| US costly clauses caught | 6 / 26 | **18 / 26** |
| US benign clauses left alone | 24 / 29 | **25 / 29** |
| England costly clauses caught | 3 / 19 | **13 / 19** |
| England benign clauses left alone | 19 / 20 | **20 / 20** |

The eleven rules added for this set were written with these clauses open, so "now" is a
regression check like the frozen corpus, not a measure of reach. `UsUkTest` pins it.

Part of what is left cannot be fixed by a rule that reads one clause. Four sentences in
the set appear under more than one state with opposite labels: a 5 percent late fee on
2,000 dollars of rent breaks New York's 50 dollar cap and is ordinary in Texas, and a two
month deposit breaks California's one month cap and is lawful in Florida. At least 6 of
the 94 rows are wrong for any rule that cannot see which jurisdiction the lease is in.
The app answers that by asking: once the reader says where the home is, the deposit,
late fee and deposit return limits follow that place's statute (`PlacesTest`). The
counts above are taken with no place chosen, as a first scan would be.

Where each row came from is in its `origin` and `source` columns:

- **paraphrase** (69 rows): written for this set from the statute, regulation or official
  guidance at `source`.
- **verbatim, US** (4 rows): form HUD-90105a, the HUD model lease, a work of the US
  government and in the public domain.
- **verbatim, England** (19 rows): the government's model tenancy agreement and the Office
  of Fair Trading's guidance on unfair tenancy terms (OFT356). Contains public sector
  information licensed under the Open Government Licence v3.0.
- **reworded** (2 rows, `u39` and `u40`): these follow the Florida Supreme Court approved
  lease form, which states no licence, so the wording here is new and only the substance
  is the form's.

### A whole US lease

`us-lease.txt` is a lease of fourteen numbered clauses (sixteen blocks as the splitter
counts them, with the title and the opening paragraph), written after the rules, in the wording US leases
use rather than the wording of this set. Nine of its clauses cost the tenant something. On
the first run the rules found six of those nine for the right reason. They read the renewal
clause ("renews automatically for a further twelve months unless the Tenant gives written
notice at least ninety days before") as a twelve month notice period, and missed two
clauses outright: the tenant accepting the flat as is and paying for all repairs, and the
rent rising at any time. Inside clauses they did flag, they missed a deposit that earns no
interest, professional carpet cleaning, and liability for the rest of the term.

Seven anchors were widened to that wording, four of them with a sentence they must not
fire on, and the notice rule now takes a figure only within 24 characters of "notice". The
lease now reads 9 of 9 costly clauses, with 14 findings, and nothing moved on the frozen
corpus, the held-out set, the 94 clauses above or the bundled sample. Having been fixed
against, the lease is a regression check now (`UsLeaseTest`), like the rest.

## What the corpus was hiding

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
[`LeaseCheck.kt`](../app/src/main/java/dev/kesav/redline/LeaseCheck.kt), which asks for two
distinct words that only tenancies use. That is loose enough to accept a single clause
shared in from another app and tight enough to reject all four documents above.

One rule was later narrowed for a reason of its own. US leases say "sole discretion" about
pets, sublets and alterations, where "the landlord alone decides what to deduct" is simply
false, so the rule now needs a deduction or the deposit in the same clause. The recipe
stopped tripping it as a side effect; the privacy policy and the job offer still trip
others, so the check still earns its place.

The first version of that list was too narrow in the other direction. An Indian leave and
licence deed names a licensor and a licensee and never says landlord, and 9 of 24 real
clauses in [`OnTopicTest`](../app/src/test/java/dev/kesav/redline/OnTopicTest.kt) were told
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
within twenty-four of *notice*. [`AdversarialTest`](../app/src/test/java/dev/kesav/redline/AdversarialTest.kt)
holds the clauses built to break it, including one with two percentages where the late
fee must come out as 4 and not 10, beside six plain clauses that must still fire. The
frozen corpus scores the same 20 of 20 and 10 of 10 afterwards, which says less about
the fix than it does about the corpus.
