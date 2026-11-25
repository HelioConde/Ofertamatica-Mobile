# OCR lab — Primor

Isolated experiment; does not modify Expo Camera or generate the production APK. Android instrumentation creates only debug/test APKs internally for the emulator. Uses bundled native ML Kit Text Recognition 16.0.1 (Latin), with no inference API charges.

## Evidence tiers
- Unit tests: eight visual transcriptions supplied by user, rejection of lot/fabrication/invalid/conflicting dates.
- Synthetic images: Android Canvas fixtures, rotated sample, bright text on dark, dot-pattern approximation.
- Real cropped photographs: upload the supplied `validade-fotos.zip` at the repository root of branch `teste-validade-ocr` (only if authorized to publish crops on the public repository). ZIP must contain 01.jpg to 08.jpg. Missing ZIP is explicitly skipped, not a pass for real-world OCR. The original photographs and personal information are not committed by this change.

The CI saves logs with expected date, raw OCR, parsed date, per-sample latency, and OK / NO_READ / WRONG_DATE. First invocation includes model initialization, emulator is not representative of phone speed. Goal: zero wrong dates and all eight real crops recognized before evaluating the Expo integration; worn cases 4 and 6 should be confirmed against packaging. Static crop test does not validate autofocus, reflections in motion or continuous frame processing. No release packaging on this branch. Next: compare unprocessed photos against preprocessing, and measure on physical devices.

## Iteration 3 — 7/8 baseline
Run 45556880592 recognized seven synthetic samples, zero wrong dates; dotted ink remained unreadable (`AL: 0B JLH 27` after preprocessing). This iteration benchmarks four fixed morphology/contrast/polarity variants only on raw no-read, requires at least two agreeing transformed candidates and rejects conflicting candidates. Expected dates are only used for scoring. Added negative image tests and stricter token/month parsing. CI requires at least 7/8 synthetic positives, zero incorrect and zero negative false positives; 8/8 remains the target. These variants are correlated, not independent camera frames; user confirmation remains mandatory. The real photo gate still requires authorized fixtures. No release build.
