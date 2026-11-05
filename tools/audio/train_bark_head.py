import argparse, csv, math, hashlib, json, struct
from pathlib import Path
from collections import Counter
import numpy as np
from scipy.io import wavfile
from scipy.signal import lfilter, resample_poly
import tensorflow as tf
from sklearn.svm import SVC
from sklearn.metrics import roc_auc_score

parser = argparse.ArgumentParser(description="Fit/export the ST274 language-independent bark head")
parser.add_argument('--corpus', type=Path, required=True)
parser.add_argument('--embedding', type=Path, required=True)
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--features-cache', type=Path)
parser.add_argument('--knocks', type=Path, required=True, help='Converted CC0 desk-knock previews')
args = parser.parse_args()
BASE, WORK = args.corpus, args.out
WORK.mkdir(parents=True, exist_ok=True)
rng=np.random.default_rng(274)
RATE=16000
BLOCK=320
TRAIN_KNOCKS = ('243033', '250139', '369710', '462317', '645927')
VALIDATION_KNOCKS = ('244325', '388269', '560225')

def held_out(source, speaker):
    return source == 'synth' or int(hashlib.sha256((source + speaker).encode()).hexdigest()[:4], 16) % 5 == 0

def events(pcm):
    noise=.001
    active=peak=quiet=0
    previous=0
    for offset in range(0,pcm.size,BLOCK):
        block=pcm[offset:offset+BLOCK]
        n=block.size
        rms=float(np.sqrt(np.mean(block*block)))
        if not active:
            if rms < max(.006,noise*3):
                noise+=(rms-noise)*.1
                previous=n
                continue
            start=offset-previous
        peak=max(peak,rms)
        if active>10400: noise+=(rms-noise)*.035
        quiet=quiet+n if rms<max(noise*1.7,peak*.22) else 0
        active+=n
        if not quiet: last_loud=offset+n
        if quiet>=960:
            if 960<=active-quiet<=10400: yield pcm[start:last_loud]
            active=peak=quiet=0
            previous=n

def load(path):
    rate,x=wavfile.read(path)
    if x.ndim>1: x=x[:,0]
    if x.dtype.kind=='i': x=x.astype(np.float32)/float(1 << (x.dtype.itemsize*8-1))
    else: x=x.astype(np.float32)
    if rate!=RATE:
        ratio=math.gcd(rate,RATE)
        x=resample_poly(x,RATE//ratio,rate//ratio)
    return x

def datasets():
    metadata={x['imitation_filename']:x['participant_id'] for x in csv.DictReader((BASE/'vocal_imitations.txt').open(),delimiter='\t')}
    for path in sorted((BASE/'positives').glob('*.wav')): yield path,1,'vocal',metadata[path.name]
    for path in sorted((BASE/'sketch').glob('*.wav')): yield path,1,'sketch',path.name.split('_')[0]
    for path in sorted((BASE/'vocalneg').glob('*.wav')): yield path,0,'vocal',path.name.split('_')[0]
    commands=sorted(p for p in (BASE/'commands').glob('*/*.wav') if not p.parent.name.startswith('_'))
    for i in rng.choice(len(commands),min(1500,len(commands)),replace=False):
        path=commands[i]
        yield path,0,'commands',path.name.split('_')[0]
    for path in sorted((BASE/'converted').glob('speech-*.wav')): yield path,0,'synth','samantha'

def embedding_inference(model):
    it=tf.lite.Interpreter(model_path=str(model),num_threads=1)
    it.allocate_tensors()
    spec=it.get_input_details()[0]
    out=it.get_output_details()[0]
    print('tensors',spec,out,flush=True)
    size=int(np.prod(spec['shape']))
    scale,zero=out['quantization']
    def infer(x):
        n=min(x.size,size)
        wave=np.zeros(size,np.float32)
        offset=(size-n)//2
        wave[offset:offset+n]=x[:n]
        it.set_tensor(spec['index'],wave.reshape(spec['shape']))
        it.invoke()
        v=it.get_tensor(out['index']).flatten().astype(np.float32)
        return (v-zero)*scale if scale else v
    return infer

def features(infer):
    rows=[]; missing=Counter(); records=Counter()
    for path,label,source,speaker in datasets():
        try: pcm=load(path)
        except ValueError: continue
        clips=list(events(np.pad(pcm,(BLOCK*4,BLOCK*5))))
        records[(source,label)]+=1
        if not clips: missing[(source,label)]+=1
        test=source == 'sketch' or held_out(source, speaker)
        for clip in clips[:10 if label else 5]:
            rows.append((infer(clip),label,source,test,path.name))
            if not test and label:
                for gain in (.45,1.35):
                    augmented=np.clip(clip*gain+rng.normal(0,.003,clip.size),-1,1).astype(np.float32)
                    rows.append((infer(augmented),label,source,test,path.name))
    print('records',records,'no events',missing,flush=True)
    return rows

def gained_events(pcm, gains, infer):
    for gain in gains:
        waveform = np.pad(np.clip(pcm * gain, -1, 1), (RATE, RATE))
        for event in events(waveform):
            yield infer(event)

def impact_features(ids, infer):
    return np.stack([
        feature for sound_id in ids
        for feature in gained_events(load(args.knocks / f'{sound_id}.wav'), (.35, .65, 1, 1.8, 3), infer)
    ])

def imitation_variants(infer):
    training, validation = [], []
    for path, label, source, speaker in datasets():
        if not label:
            continue
        pcm = load(path)
        variants = gained_events(pcm, (.3, .6, 1.5, 2.5), infer)
        if held_out(source, speaker):
            validation.extend(variants)
            continue
        training.extend(variants)
        # Simulate a muffled microphone signal and two room reflections.
        room = .45 * lfilter([1], [1, -.73], pcm)
        room[240:] += .25 * room[:-240]
        room[592:] += .1 * room[:-592]
        training.extend(gained_events(room, (.45, 1., 1.8), infer))
    return np.stack(training), np.stack(validation)

def main():
    model = WORK / 'bark-embedding.tflite'
    model.write_bytes(args.embedding.read_bytes())
    infer = embedding_inference(model)
    cache = args.features_cache or WORK / 'features.npz'
    if cache.exists():
        f = np.load(cache)
        X, y, source, clip = (f[k] for k in ('X', 'y', 'source', 'clip'))
    else:
        rows = features(infer)
        X = np.stack([r[0] for r in rows])
        y = np.array([r[1] for r in rows])
        source = np.array([r[2] for r in rows])
        clip = np.array([r[4] for r in rows])
        np.savez_compressed(cache, X=X, y=y, source=source, clip=clip)
    metadata = {r['imitation_filename']: r['participant_id'] for r in
                csv.DictReader((BASE / 'vocal_imitations.txt').open(), delimiter='\t')}
    speaker = [metadata.get(c, c.split('_')[0]) if s == 'vocal' else c.split('_')[0]
               for s, c in zip(source, clip)]
    validation = np.array([held_out(s, p) for s, p in zip(source, speaker)])
    knocks = impact_features(TRAIN_KNOCKS, infer)
    held_out_knocks = impact_features(VALIDATION_KNOCKS, infer)
    imitations, held_out_imitations = imitation_variants(infer)
    train_features = np.concatenate((X[~validation], knocks, imitations))
    train_labels = np.r_[y[~validation], np.zeros(len(knocks)), np.ones(len(imitations))]
    # Emphasize known false triggers while limiting the weight of correlated augmentations.
    weights = np.r_[np.ones(sum(~validation)), np.full(len(knocks), 3), np.full(len(imitations), .4)]
    classifier = SVC(C=20, gamma='scale', class_weight='balanced', random_state=274)
    classifier.fit(train_features, train_labels, sample_weight=weights)
    # A fixed positive decision margin favors rejection of ordinary speech.
    # Selected on validation; no language-specific words or device-specific tuning.
    threshold = 0.85
    vectors, dimensions = classifier.support_vectors_.shape
    binary = struct.pack('<iiifff', 0x31484B42, dimensions, vectors, classifier._gamma,
                         classifier.intercept_[0], threshold)
    binary += classifier.dual_coef_[0].astype('<f4').tobytes()
    binary += classifier.support_vectors_.astype('<f4').tobytes()
    (WORK / 'bark-head.bin').write_bytes(binary)
    scores = classifier.decision_function(X[validation])
    labels = y[validation]
    predictions = scores >= threshold
    card = dict(format='BKH1 little endian', sampleRate=RATE, embeddingDimensions=dimensions,
                supportVectors=vectors, threshold=float(threshold), seed=274,
                trainingEvents=len(train_labels), trainingKnockEvents=len(knocks),
                trainingImitationVariants=len(imitations), validationEvents=int(sum(validation)),
                validationPositiveEvents=int(sum(labels == 1)),
                validationAcceptedPositiveEvents=int(sum(predictions & (labels == 1))),
                validationNegativeEvents=int(sum(labels == 0)),
                validationAcceptedNegativeEvents=int(sum(predictions & (labels == 0))),
                validationAUC=float(roc_auc_score(labels, scores)),
                validationKnockEvents=len(held_out_knocks),
                validationAcceptedKnockEvents=int(sum(classifier.decision_function(held_out_knocks) >= threshold)),
                validationImitationVariants=len(held_out_imitations),
                validationAcceptedImitationVariants=int(sum(classifier.decision_function(held_out_imitations) >= threshold)),
                featureCacheSHA256=hashlib.sha256(cache.read_bytes()).hexdigest(),
                assets={p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in (model, WORK / 'bark-head.bin')},
                limits='Event labels inherit source-clip labels; breathing/clicks in positive clips are not manually aligned. '
                       'Validation guided model selection and is not a blind population test. '
                       'No user microphone recordings used for fitting.')
    (WORK / 'model-card.json').write_text(json.dumps(card, indent=2) + '\n')
    print(json.dumps(card, indent=2), flush=True)

if __name__ == '__main__':
    main()
