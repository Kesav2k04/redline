# Not built, on purpose

Everything here was considered and left out. Keeping the list is cheaper than
rediscovering the reasoning later, and a few of these are the obvious next moves.

## Worth building next

**More rule coverage.** Thirty-eight rules cover the clauses that cost people money in
Indian residential leases, with US and English clauses added since. Commercial leases, employment contracts and loan agreements
have their own set, and the engine does not care which set it loads.

**Rules as data rather than code.** The patterns are already declarative; moving them to
a bundled file would let the bank update without shipping an APK, and would let someone
contribute a rule without writing Kotlin.

**More places.** Six are in (`Places.kt`): California, New York, Massachusetts, Texas,
England and India, each figure quoted from its statute. More US states are the obvious
next step, and India would be better served state by state, since the Model Tenancy Act is
a proposal that Maharashtra, Karnataka and Delhi have not adopted.

Places also need rules of their own, not only limits. "A non-refundable pet fee of $300"
is unlawful in California, which bars calling any security non-refundable (Civil Code
1950.5(n)), and in New York, which allows no fee at the start of a tenancy beyond a
background and credit check (Real Property Law 238-a). The same sentence is ordinary in
Texas and Illinois, and `intl.tsv` carries all four. A rule that runs only once the reader
has named one of those places would catch it without flagging the other two.

**Deadline reminders.** A lease that renews itself unless notice is given sixty days
before the end already trips the auto-renewal rule. Reading the end date and the notice
period would let the app put one reminder in the calendar, through the calendar app's own
insert screen, so no permission is needed.

**Other languages.** The clause splitter and the number reader assume English. A lease
in Hindi or Tamil would need its own number words, headings and rules.

## Built since this list was first written

**Reading PDFs and paper.** The original entry below said no: Android could only
rasterise PDF pages, and the libraries that extract text were heavy or AGPL. Android 15
added a text layer to `PdfRenderer`, which covers digital PDFs on current phones. Scanned
PDFs, older phones and photographs of paper go through ML Kit's bundled text recognition,
which is on-device and Apache licensed. The column and hyphen problems were real, so
imported pages are rebuilt into paragraphs and a test holds a PDF to the same findings as
the same lease pasted.

**Export, then a letter.** The report goes out through the share sheet with each clause
quoted. Every rule now also says what to ask for, and a separate letter asks the landlord
for those changes without quoting the scanner's verdicts back at them.

## Deliberately not building

**A bundled PDF library.** PDFBox-Android is heavy and slow to initialise, and iText is
AGPL, which would change the licence of this repository. The platform text layer plus
on-device OCR covers the same ground without either.

**An account system.** Nothing here needs to know who you are. A lease is a private
document, and the strongest privacy promise is one the code enforces: the lease never
touches the network. The only traffic is RevenueCat's, for the price and the purchase.

**Cloud analysis or a language model.** It would cost money per scan, require sending
somebody's lease to a server, and produce answers nobody can check. An on-device model
avoids the first two and not the third: the eval in `eval/` tested embeddings, not a
language model, and the case against one here is that a flag has to point at a sentence
and give the same answer twice, which rules do by construction.

**A second embedding attempt with a larger model.** EmbeddingGemma separates far better
than Universal Sentence Encoder, but the file is 183 MB and fetching it needs a licence
acceptance step that a clean clone cannot perform unattended, so the repository would
stop building for anyone else. That trade was not worth making.

**Tiered pricing.** One purchase, one thing bought. Three tiers on an app with one
feature is a pricing page pretending to be a product.
