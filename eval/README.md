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

1. Top-1 category correct on at least **14 of the 20** costly clauses. Chance is about
   0.83%, so 14 cannot happen by luck.
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
  scored a margin of 0.1610, higher than fourteen of the twenty genuinely costly clauses.
  `b08`, about paying your own electricity bill, scored 0.1759, the second highest margin
  in the whole set.

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
