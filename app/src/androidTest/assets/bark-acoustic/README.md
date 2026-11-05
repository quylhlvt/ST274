PCM16 little endian, mono, 16 kHz. No user microphone recordings.

`bark-1` / `bark-2`: excerpts from Vocal Imitation Set v1.0 by Bongjun Kim
and Bryan Pardo (Northwestern University), CC BY 4.0.
https://doi.org/10.5281/zenodo.1249724

`bark-3` / `bark-4`: excerpts from VocalSketch v1.0.4 by Mark Cartwright
and Bryan Pardo (Northwestern University), CC BY 4.0.
https://doi.org/10.5281/zenodo.13862

These are selected clear imitations from four speakers excluded from head
training. Original filenames, speaker IDs and selected-event scores are in
sources.json. Selection is deliberate: these fixtures test runtime equivalence
and repeated-event counting, not population accuracy. Broader validation
results and false-positive limits are documented in docs/bark-detection.md.
Edits: mono resampling to 16 kHz and extraction of individual short events.

`bark-previously-missed` is a 200 ms excerpt (event index 7, zero-based) from
Vocal Imitation Set file
`004Animal_Domestic animals_ pets_Dog_Bark-6408266225549312.wav`, held-out
participant `6020600000000000.0`. The original model scored it below threshold.

`speech-en`, `speech-de`, `speech-fr`, `speech-es`: ordinary sentences generated
locally with macOS say at rate 165 (Daniel, Anna, Thomas, Eddy Spanish Spain),
then converted with afconvert. These synthetic sentences are regression
fixtures; they do not establish rejection rates for all languages or voices.

The held-out desk knock fixtures are from Freesound (CC0), converted from HQ MP3
previews to mono 16 kHz PCM. The tests vary their volume.

- `desk-knock-244325`: Philip_Daniels, https://freesound.org/people/Philip_Daniels/sounds/244325/
- `desk-knock-388269`: Sami Kullström, https://freesound.org/people/Sami_Kullstr%C3%B6m/sounds/388269/
- `desk-knock-560225`: DanaGarcia, https://freesound.org/people/DanaGarcia/sounds/560225/
