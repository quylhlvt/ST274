# Acoustic bark head

Gameplay classifies audio; it does not use speech recognition or a vocabulary.
See `docs/bark-detection.md` for measured limitations before changing thresholds.

The export script requires Python 3.11, TensorFlow 2.16.2, NumPy 1.26.4,
SciPy 1.17.1, scikit-learn 1.9.0; it uses the YAMNet embedding already present
at `app/src/main/assets/audio/bark-embedding.tflite`.

The local corpus directory has these inputs:

- `vocal_imitations.txt`: Vocal Imitation Set metadata (CC BY 4.0).
- `positives/*.wav`: 20 human dog-bark imitation recordings from that set.
- `vocalneg/*.wav`: 74 other human sound imitations from that set.
- `sketch/*.wav`: 30 dog-bark imitation recordings from VocalSketch (CC BY 4.0),
  with participant IDs as filename prefixes.
- `commands/<word>/*.wav`: Speech Commands v0.02 test set (CC BY 4.0).
- `converted/speech-*.wav`: eight ordinary synthetic English speech clips.

Dataset links and authors are listed in the packaged `audio/NOTICE.txt`.
Do not substitute real dog recordings for the human imitation positives.
Do not include private microphone recordings in the corpus or app assets.

```sh
python tools/audio/train_bark_head.py \
  --corpus /path/to/corpus \
  --knocks /path/to/desk-knocks \
  --embedding app/src/main/assets/audio/bark-embedding.tflite \
  --out /tmp/bark-export
```

The script extracts short events using the runtime segmentation rules, uses
the packaged pooled 1024-dimensional embedding from YAMNet, and fits an RBF SVM head.
Splits are by participant, with a deterministic seed. The training imitations
are augmented with gain and room reflections. Ordinary synthetic speech is
validation only. The model card records validation errors and asset hashes.

The knock directory contains the Freesound HQ MP3 previews converted to mono
16 kHz PCM16 WAVs, named by sound ID. See the packaged audio notice for training
sources and the test fixture README for the three validation sources. All three
validation recordings are held out as whole clips.

For an exact re-export from an existing feature matrix, pass
`--features-cache /path/to/features.npz`. The cache must contain `X`, `y`,
`source`, and `clip`; its SHA-256 is recorded in `model-card.json`.
The original cache and full corpus are local training inputs, not app assets.

After evaluating the export, copy `bark-embedding.tflite`, `bark-head.bin`, and
`model-card.json` into `app/src/main/assets/audio`, preserving the notices.
Run `BarkAcousticModelTest` and the Play/Training screen tests on a device.

`BKH1` format: little-endian magic `0x31484B42`, int32 feature count, int32
support-vector count, float32 gamma, bias, and acceptance margin; then
float32 signed coefficients and row-major support vectors. The Android head
uses double accumulation and rejects non-finite embeddings.
