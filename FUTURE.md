# Not built, on purpose

Everything here was considered and left out. Keeping the list is cheaper than
rediscovering the reasoning later, and a few of these are the obvious next moves.

## Worth building next

**More rule coverage.** Twenty-seven rules cover the clauses that cost people money in
Indian residential leases. Commercial leases, employment contracts and loan agreements
have their own set, and the engine does not care which set it loads.

**Rules as data rather than code.** The patterns are already declarative; moving them to
a bundled file would let the bank update without shipping an APK, and would let someone
contribute a rule without writing Kotlin.

**Jurisdiction-aware limits.** "Several states cap deposits at two or three months" is
currently prose in a reason string. It should be a table, keyed by state, so the app can
say which limit a clause actually breaches.

**Optical character recognition.** ML Kit Text Recognition over a photo of a page would
cover leases that only exist on paper. It is free, on-device and Apache licensed. It was
left out because the input path had to be certain before the clever part got attention.

**Export.** A shareable summary the tenant can send to the landlord, or to a lawyer,
with the clauses and the reasons.

## Deliberately not building

**A PDF reader.** Android has no text extraction: `PdfRenderer` only rasterises pages.
The options are PDFBox-Android, which is heavy and slow to initialise, or iText, which
is AGPL and would change the licence of this repository. Real lease PDFs also arrive
hyphenated and column-shuffled, and a scanned one has no text layer at all. Shared and
pasted text covers the same need with none of that.

**An account system.** Nothing here needs to know who you are. A lease is a private
document and the strongest privacy promise is the one enforced by the absence of a
network call.

**Cloud analysis or a language model.** It would cost money per scan, require sending
somebody's lease to a server, and produce answers nobody can check. The measurement in
`eval/` is the argument against the on-device version of the same idea.

**A second embedding attempt with a larger model.** EmbeddingGemma separates far better
than Universal Sentence Encoder, but the file is 183 MB and fetching it needs a licence
acceptance step that a clean clone cannot perform unattended, so the repository would
stop building for anyone else. That trade was not worth making.

**Tiered pricing.** One purchase, one thing bought. Three tiers on an app with one
feature is a pricing page pretending to be a product.
