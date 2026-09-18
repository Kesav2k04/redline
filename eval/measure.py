"""Measures whether cosine ranking over the archetype bank can actually tell a
costly lease clause from a benign one.

The corpus in clauses.tsv was written and committed before archetypes.tsv existed,
so the archetypes could not be fitted to the test. Both files are frozen inputs.

Pass conditions, fixed before the first run:
  1. top-1 category correct on at least 14 of the 20 costly clauses
  2. median true-positive margin at least 3x the 90th-percentile benign margin

Margin is the gap between the winning archetype and the best archetype belonging to
a different category. Measuring against the runner-up overall would just measure how
similar two archetypes in the same category are, which is not the question.
"""

import csv
import statistics
import sys
from pathlib import Path

import numpy as np
from mediapipe.tasks import python as mp
from mediapipe.tasks.python import text

HERE = Path(__file__).parent
MODEL = HERE / "models" / "universal_sentence_encoder.tflite"


def load(name):
    with open(HERE / name, encoding="utf-8") as fh:
        return list(csv.DictReader(fh, delimiter="\t"))


def embed_all(texts):
    options = text.TextEmbedderOptions(
        base_options=mp.BaseOptions(model_asset_path=str(MODEL)),
        l2_normalize=False,
        quantize=False,
    )
    with text.TextEmbedder.create_from_options(options) as embedder:
        return np.array([embedder.embed(t).embeddings[0].embedding for t in texts])


def cosine_matrix(a, b):
    a = a / np.linalg.norm(a, axis=1, keepdims=True)
    b = b / np.linalg.norm(b, axis=1, keepdims=True)
    return a @ b.T


def evaluate(clause_vecs, arch_vecs, clauses, archetypes, label):
    sims = cosine_matrix(clause_vecs, arch_vecs)
    arch_cats = [a["category"] for a in archetypes]

    correct = 0
    tp_margins, benign_margins = [], []
    rows = []

    for i, clause in enumerate(clauses):
        row = sims[i]
        best = int(np.argmax(row))
        predicted = arch_cats[best]

        other = [j for j in range(len(arch_cats)) if arch_cats[j] != predicted]
        margin = float(row[best] - row[max(other, key=lambda j: row[j])])

        if clause["label"] == "costly":
            hit = predicted == clause["category"]
            correct += hit
            if hit:
                tp_margins.append(margin)
        else:
            benign_margins.append(margin)

        rows.append((clause["id"], clause["label"], clause["category"],
                     predicted, float(row[best]), margin))

    benign_p90 = float(np.percentile(benign_margins, 90))
    tp_median = statistics.median(tp_margins) if tp_margins else 0.0
    ratio = tp_median / benign_p90 if benign_p90 > 0 else float("inf")

    print(f"\n{'=' * 66}\n{label}\n{'=' * 66}")
    print(f"{'id':5} {'truth':7} {'true cat':17} {'predicted':17} {'cos':>6} {'margin':>7}")
    for r in rows:
        flag = " " if (r[1] == "benign" or r[2] == r[3]) else "X"
        print(f"{r[0]:5} {r[1]:7} {r[2]:17} {r[3]:17} {r[4]:6.3f} {r[5]:7.4f} {flag}")

    print(f"\n  cosine range over all pairs : {sims.min():.4f} to {sims.max():.4f}")
    print(f"  top-1 category correct      : {correct}/20   (pass needs 14)")
    print(f"  median true-positive margin : {tp_median:.4f}")
    print(f"  90th pct benign margin      : {benign_p90:.4f}")
    print(f"  ratio                       : {ratio:.2f}x   (pass needs 3.00)")

    passed = correct >= 14 and ratio >= 3.0
    print(f"  VERDICT                     : {'PASS' if passed else 'FAIL'}")
    return passed


def main():
    clauses = load("clauses.tsv")
    archetypes = load("archetypes.tsv")
    print(f"{len(clauses)} clauses, {len(archetypes)} archetypes, model {MODEL.stat().st_size} bytes")

    clause_vecs = embed_all([c["text"] for c in clauses])
    arch_vecs = embed_all([a["text"] for a in archetypes])
    print(f"embedding dimensionality: {clause_vecs.shape[1]}")

    raw = evaluate(clause_vecs, arch_vecs, clauses, archetypes, "RAW COSINE")

    # Mean-centering is the standard correction for the anisotropy that squeezes
    # this model's cosine range. The mean comes from the archetype bank only, so
    # nothing about the test set leaks into the transform.
    centre = arch_vecs.mean(axis=0)
    centred = evaluate(clause_vecs - centre, arch_vecs - centre,
                       clauses, archetypes, "MEAN-CENTERED")

    print(f"\n{'=' * 66}")
    print(f"raw: {'PASS' if raw else 'FAIL'}    centered: {'PASS' if centred else 'FAIL'}")
    return 0 if (raw or centred) else 1


if __name__ == "__main__":
    sys.exit(main())
