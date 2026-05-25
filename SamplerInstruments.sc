
+ SSampler {
		*initClass{

		allSampler = IdentityDictionary.new;
		defaultLoadingServer = Server.default;

		//from Halim Beere and Henrich Taube
		StartUp.add({
			SynthDef(\ssplaybuf1, {arg buf, rate = 1, dur = 1, amp = 1, pan = 0, bend=nil, out = 0, startPos = 0;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);  //ampenv parameter
				var antiClipEnv = Env.linen(0.005, dur, 0.005, amp, \sine);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: antiClipEnv.duration);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: antiClipEnv.duration * skwgen.reciprocal, doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, pan, timeScale: antiClipEnv.duration * skwgen.reciprocal);
				var source = ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf, rate: rate * skwgen * BufRateScale.kr(buf), startPos: startPos * BufSampleRate.kr(buf));
				Out.ar(bus: out, channelsArray: Pan2.ar(in: source * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), pos: pangen));
			}).add;

			SynthDef(\ssplaybuf2, {arg buf0, buf1, rate = 1, dur = 1, amp = 1, pan = 0, bend =  nil, out = 0, startPos = 0;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);  //ampenv parameter
				var antiClipEnv = Env.linen(0.005, dur, 0.005, amp, \sine);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: antiClipEnv.duration);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: antiClipEnv.duration * skwgen.reciprocal, doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, pan, timeScale: antiClipEnv.duration * skwgen.reciprocal);
				var source0 = ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf0, rate: rate * skwgen * BufRateScale.kr(buf0), startPos: startPos * BufSampleRate.kr(buf0));
				var source1 = ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf1, rate: rate * skwgen * BufRateScale.kr(buf1), startPos: startPos * BufSampleRate.kr(buf1));
				Out.ar(bus: out, channelsArray: Balance2.ar(source0 * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), source1 * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), pos: pangen));
			}).add;


			SynthDef(\ssexpand1, {arg buf, expand=1, dur=1, rate=1, amp=1, pan=0, bend = nil, out=0, grainRate=20, grainDur=0.15, startPos = 0, panSpread = 0.1;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: dur * expand);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: dur * expand , doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, pan, timeScale: dur * expand);
				var trigger = Impulse.kr(grainRate + LFNoise0.kr(grainRate*2,2.0/grainDur));
				var position = Line.kr(start: startPos/BufDur.ir(buf), end: (rate.sign + 1)/2 * 0.95, dur:  (dur * expand));  //dur:  ((BufDur.ir(buf) - startPos) * expand));
				var outsig = ampgen * GrainBuf.ar(numChannels: 2, trigger: trigger, dur: grainDur, sndbuf: buf, rate: rate * skwgen,
				pos: position, interp: 2, pan: TRand.kr(panSpread * -1,panSpread,trigger) + pangen );
				Out.ar(bus: out, channelsArray: outsig);
			}).add;

			SynthDef(\ssexpand2, {arg buf0, buf1, expand=1, dur=1, rate=1, amp=1, pan=0, bend = nil, out=0, grainRate=20, grainDur=0.15, startPos = 0, panSpread = 0.1;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: dur * expand);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: dur * expand , doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, pan, timeScale: dur * expand);
				var trigger = Impulse.kr(grainRate + LFNoise0.kr(grainRate*2,2.0/grainDur));
				var position = Line.kr(start: startPos/BufDur.ir(buf0), end: (rate.sign + 1)/2 * 0.95, dur:  (dur * expand));  //dur:  ((BufDur.ir(buf) - startPos) * expand));
				var outsig0 = ampgen * GrainBuf.ar(numChannels: 1, trigger: trigger, dur: grainDur, sndbuf: buf0, rate: rate * skwgen * BufRateScale.kr(buf0),
				pos: position, interp: 2, pan: -1);
				var outsig1 = ampgen * GrainBuf.ar(numChannels: 1, trigger: trigger, dur: grainDur, sndbuf: buf1, rate: rate * skwgen * BufRateScale.kr(buf1),
				pos: position, interp: 2, pan: 1);
				Out.ar(bus: out, channelsArray: Balance2.ar(outsig0, outsig1, pangen));
			}).add;


			// Voice-mode mono SynthDef.
			// Lifetime is owned by a single EnvGen with doneAction:2.
			// Caller (SamplerPrepare#playVoice) supplies the envelope shape:
			//   - one-shot: Env.linen(attack, body, release) -> self-frees at end.
			//   - gated:    Env.asr(attack, 1, release) + gate -> released via .set(\gate, 0).
			//
			// loop = 0: Line.ar pointer over the whole section, rate baked into dur.
			// loop = 1: Phasor.ar wraps between [loopStart, loopEnd] at rate*BufRateScale.
			// loopDir : 0 fwd, 1 rev, 2 palin.
			// loopMode: 0 trapezoid window (default), 1 equal-power 2-tap crossfade.
			//   trapezoid: cheap, sharper, optionally disabled via loopXfade <= 0.
			//   xfade:     continuous sin/cos crossfade between ptr and a half-cycle
			//              offset reader. Doubles BufRd cost; phaseyness on
			//              slowly-varying material. Palindrome falls back to ptr
			//              (no seam to mask).
			//
			// Release region (Ableton-style second loop): on note-off (gate -> 0)
			// the playhead crossfades from the sustain pointer to a release-region
			// pointer over releaseXfade seconds. The release region lives inside
			// the same buffer at [releaseStart, releaseEnd].
			// releaseMode: 0 off, 1 oneShot, 2 loop (fwd), 3 palin (back-and-forth).
			// The release region is audible only while the amp envelope's release
			// segment is still ramping down -- same caveat as Ableton's Sampler.
			SynthDef(\ssvoice1, {arg buf, rate = 1, amp = 1, pan = 0, out = 0,
				                startPos = 0, dur = 1, gate = 1,
				                loop = 0, loopDir = 0, loopMode = 0,
				                loopStart = 0, loopEnd = 0,
				                loopXfade = 0,
				                releaseMode = 0, releaseStart = 0, releaseEnd = 0,
				                releaseXfade = 0;
				var envCtl = \env.kr(Env.newClear(8).asArray);
				var bufSR = BufSampleRate.kr(buf);
				var bufFrames = BufFrames.kr(buf);
				var startFrames = startPos * bufSR;
				var step = rate * BufRateScale.kr(buf);
				// loopEnd <= 0 means "use whole buffer".
				var lEnd = Select.kr(loopEnd > 0, [bufFrames, loopEnd]);
				var lStart = loopStart;
				var llen = (lEnd - lStart).max(1);
				var halfLen = llen * 0.5;
				var fwd = Phasor.ar(0, step,     lStart, lEnd, lStart);
				var rev = Phasor.ar(0, step.neg, lStart, lEnd, lEnd);
				// Palindrome: unfolded triangle phase tri in [0, 2*llen),
				// folded into [lStart, lEnd] by reflecting around llen.
				var tri = Phasor.ar(0, step.abs, 0, 2 * llen);
				var pal = lStart + (llen - (tri - llen).abs);
				// Half-cycle-offset readers for the xfade mode.
				var fwdAlt = Phasor.ar(0, step,     lStart, lEnd, lStart + halfLen);
				var revAlt = Phasor.ar(0, step.neg, lStart, lEnd, lEnd   - halfLen);
				var loopPtr  = Select.ar(loopDir, [fwd,    rev,    pal]);
				var loopPtr2 = Select.ar(loopDir, [fwdAlt, revAlt, pal]);
				var onePtr = Line.ar(startFrames, startFrames + bufFrames, dur);
				var ptr  = Select.ar(loop, [onePtr, loopPtr]);
				var ptr2 = Select.ar(loop, [onePtr, loopPtr2]);
				var sig1 = BufRd.ar(1, buf, ptr,  loop: 1, interpolation: 4);
				var sig2 = BufRd.ar(1, buf, ptr2, loop: 1, interpolation: 4);
				// Normalized loop-cycle phase: 0 at lStart, 1 at lEnd. Same
				// expression handles fwd (sawtooth 0->1), rev (sawtooth 1->0),
				// pal (triangle 0->1->0).
				var ph = ((ptr - lStart) / llen).clip(0, 1);
				// loopMode 0: trapezoid window.
				var f = ((loopXfade * bufSR) / llen).max(0.0001);
				var winRaw = ((ph / f).clip(0, 1) * ((1 - ph) / f).clip(0, 1));
				var winEnabled = loop * (loopXfade > 0);
				var win = (winEnabled * winRaw) + (1 - winEnabled);
				var sigTrap = sig1 * win;
				// loopMode 1: equal-power 2-tap crossfade. g1*sig1 + g2*sig2
				// with g1^2 + g2^2 = 1; g1 = 0 at the seam (ptr at lStart for fwd
				// or at lEnd-equivalent for rev), masking the discontinuity.
				// Palin has no seam, so we fall back to sig1.
				var g1 = sin(ph * (pi/2));
				var g2 = cos(ph * (pi/2));
				var sigSeam = (sig1 * g1) + (sig2 * g2);
				var isPalin = (loopDir > 1);
				var sigXfade = ((1 - isPalin) * sigSeam) + (isPalin * sig1);
				var sigLoop = Select.ar(loopMode, [sigTrap, sigXfade]);
				// One-shot is always sig1 (loopMode irrelevant).
				var sustainSig = Select.ar(loop, [sig1, sigLoop]);
				// ---- Release region (Ableton-style) ----
				// noteOffTrig fires once on gate -> 0 (one shot per synth lifetime,
				// since voice synths are never re-gated; a re-press makes a new node).
				var noteOffTrig = 1 - gate;
				var rEnd = Select.kr(releaseEnd > 0, [bufFrames, releaseEnd]);
				var rStart = releaseStart;
				var rLen = (rEnd - rStart).max(1);
				var rFwd = Phasor.ar(noteOffTrig, step,     rStart, rEnd, rStart);
				var rTri = Phasor.ar(noteOffTrig, step.abs, 0, 2 * rLen);
				var rPal = rStart + (rLen - (rTri - rLen).abs);
				// One-shot: linear sweep from rStart starting at noteOff, clamped at rEnd.
				// Sweep is units-per-second; multiply step (frames/sample) by SR.
				var rSweep = Sweep.ar(noteOffTrig, step * SampleRate.ir);
				var rOneShotPtr = (rStart + rSweep).min(rEnd);
				// Slot 0 is a stable rStart placeholder. releaseMode 0 keeps the
				// release contribution muted via fadeAmt = 0 below, so the value
				// here only matters in so far as it makes BufRd a cheap no-op.
				var releasePtr = Select.ar(releaseMode, [
					DC.ar(0),
					rOneShotPtr,
					rFwd,
					rPal
				]);
				var releaseSig = BufRd.ar(1, buf, releasePtr, loop: 1, interpolation: 4);
				// Equal-power crossfade from sustain pointer to release pointer.
				// releaseActive masks the fade to 0 when releaseMode == \off so that
				// the existing sustain-mode behavior is byte-identical.
				var releaseActive = releaseMode > 0;
				var fadeAmt = Lag.kr(noteOffTrig * releaseActive, releaseXfade.max(0));
				var sustainGain = K2A.ar(cos(fadeAmt * (pi/2)));
				var releaseGain = K2A.ar(sin(fadeAmt * (pi/2)));
				var sig = (sustainSig * sustainGain) + (releaseSig * releaseGain);
				var env = EnvGen.kr(envCtl, gate, doneAction: 2);
				Out.ar(out, Pan2.ar(sig * env * amp, pan));
			}).add;


			// Voice-mode stereo SynthDef. Mirrors \ssvoice1 exactly except for:
			//   - two source buffers (buf0 = L, buf1 = R)
			//   - Balance2 stereo positioning
			// Pointer math is shared and derives sample rate / frame count from
			// buf0 (the loader always allocates L/R as a matched pair).
			SynthDef(\ssvoice2, {arg buf0, buf1, rate = 1, amp = 1, pan = 0, out = 0,
				                startPos = 0, dur = 1, gate = 1,
				                loop = 0, loopDir = 0, loopMode = 0,
				                loopStart = 0, loopEnd = 0,
				                loopXfade = 0,
				                releaseMode = 0, releaseStart = 0, releaseEnd = 0,
				                releaseXfade = 0;
				var envCtl = \env.kr(Env.newClear(8).asArray);
				var bufSR = BufSampleRate.kr(buf0);
				var bufFrames = BufFrames.kr(buf0);
				var startFrames = startPos * bufSR;
				var step = rate * BufRateScale.kr(buf0);
				var lEnd = Select.kr(loopEnd > 0, [bufFrames, loopEnd]);
				var lStart = loopStart;
				var llen = (lEnd - lStart).max(1);
				var halfLen = llen * 0.5;
				var fwd = Phasor.ar(0, step,     lStart, lEnd, lStart);
				var rev = Phasor.ar(0, step.neg, lStart, lEnd, lEnd);
				var tri = Phasor.ar(0, step.abs, 0, 2 * llen);
				var pal = lStart + (llen - (tri - llen).abs);
				var fwdAlt = Phasor.ar(0, step,     lStart, lEnd, lStart + halfLen);
				var revAlt = Phasor.ar(0, step.neg, lStart, lEnd, lEnd   - halfLen);
				var loopPtr  = Select.ar(loopDir, [fwd,    rev,    pal]);
				var loopPtr2 = Select.ar(loopDir, [fwdAlt, revAlt, pal]);
				var onePtr = Line.ar(startFrames, startFrames + bufFrames, dur);
				var ptr  = Select.ar(loop, [onePtr, loopPtr]);
				var ptr2 = Select.ar(loop, [onePtr, loopPtr2]);
				var sig1L = BufRd.ar(1, buf0, ptr,  loop: 1, interpolation: 4);
				var sig1R = BufRd.ar(1, buf1, ptr,  loop: 1, interpolation: 4);
				var sig2L = BufRd.ar(1, buf0, ptr2, loop: 1, interpolation: 4);
				var sig2R = BufRd.ar(1, buf1, ptr2, loop: 1, interpolation: 4);
				var ph = ((ptr - lStart) / llen).clip(0, 1);
				// Trapezoid window (same shape for L and R).
				var f = ((loopXfade * bufSR) / llen).max(0.0001);
				var winRaw = ((ph / f).clip(0, 1) * ((1 - ph) / f).clip(0, 1));
				var winEnabled = loop * (loopXfade > 0);
				var win = (winEnabled * winRaw) + (1 - winEnabled);
				var sigTrapL = sig1L * win;
				var sigTrapR = sig1R * win;
				// Equal-power 2-tap crossfade.
				var g1 = sin(ph * (pi/2));
				var g2 = cos(ph * (pi/2));
				var isPalin = (loopDir > 1);
				var sigSeamL = (sig1L * g1) + (sig2L * g2);
				var sigSeamR = (sig1R * g1) + (sig2R * g2);
				var sigXfadeL = ((1 - isPalin) * sigSeamL) + (isPalin * sig1L);
				var sigXfadeR = ((1 - isPalin) * sigSeamR) + (isPalin * sig1R);
				var sigLoopL = Select.ar(loopMode, [sigTrapL, sigXfadeL]);
				var sigLoopR = Select.ar(loopMode, [sigTrapR, sigXfadeR]);
				var sustainSigL = Select.ar(loop, [sig1L, sigLoopL]);
				var sustainSigR = Select.ar(loop, [sig1R, sigLoopR]);
				// ---- Release region (Ableton-style) ----
				var noteOffTrig = 1 - gate;
				var rEnd = Select.kr(releaseEnd > 0, [bufFrames, releaseEnd]);
				var rStart = releaseStart;
				var rLen = (rEnd - rStart).max(1);
				var rFwd = Phasor.ar(noteOffTrig, step,     rStart, rEnd, rStart);
				var rTri = Phasor.ar(noteOffTrig, step.abs, 0, 2 * rLen);
				var rPal = rStart + (rLen - (rTri - rLen).abs);
				var rSweep = Sweep.ar(noteOffTrig, step * SampleRate.ir);
				var rOneShotPtr = (rStart + rSweep).min(rEnd);
				var releasePtr = Select.ar(releaseMode, [
					DC.ar(0),
					rOneShotPtr,
					rFwd,
					rPal
				]);
				var releaseSigL = BufRd.ar(1, buf0, releasePtr, loop: 1, interpolation: 4);
				var releaseSigR = BufRd.ar(1, buf1, releasePtr, loop: 1, interpolation: 4);
				var releaseActive = releaseMode > 0;
				var fadeAmt = Lag.kr(noteOffTrig * releaseActive, releaseXfade.max(0));
				var sustainGain = K2A.ar(cos(fadeAmt * (pi/2)));
				var releaseGain = K2A.ar(sin(fadeAmt * (pi/2)));
				var sigL = (sustainSigL * sustainGain) + (releaseSigL * releaseGain);
				var sigR = (sustainSigR * sustainGain) + (releaseSigR * releaseGain);
				var env = EnvGen.kr(envCtl, gate, doneAction: 2);
				Out.ar(out, Balance2.ar(sigL * env * amp, sigR * env * amp, pan));
			}).add;


			// PaulStretch-style per-section time stretch (mono).
			//
			// Pitch-preserving extreme time stretch in the spirit of Paul
			// Nasca's PaulStretch. Two stages:
			//   1. Warp1 traverses the section slowly. The read pointer sweeps
			//      across the buffer at a speed of one buffer-length per `dur`
			//      seconds (the caller bakes the stretch factor into `dur`).
			//      With loop == 0 the pointer is a one-shot Line start -> end;
			//      with loop == 1 it is a Phasor that wraps inside the
			//      [loopStart, loopEnd] region (loopDir: 0 fwd, 1 rev, 2 palin),
			//      so loop points work at any stretch amount. Grains play at
			//      freqScale = rate, so pitch is owned by `rate` (rate = 1 ->
			//      original pitch) and is *independent* of the stretch amount --
			//      stretching never changes pitch.
			//   2. FFT -> PV_Diffuser -> IFFT randomizes the phase of every bin
			//      each frame while preserving magnitudes. This is the
			//      characteristic PaulStretch move: it dissolves the periodic
			//      grain-rate artifacts Warp1 leaves behind into the smooth,
			//      smeared spectral wash PaulStretch is known for, and also
			//      masks the loop seam (no explicit crossfade needed).
			//
			// fftSize is a SynthDef-build constant (LocalBuf requires it). The
			// grain window length and overlap count are exposed as controls.
			// loopMode / loopXfade and the release region do not apply here.
			// Lifetime is the gate-driven \env (doneAction:2), same contract as
			// \ssvoice1: one-shot callers pass a self-terminating linen sized to
			// the stretched duration; gated/looped callers hold the note open
			// and drop gate to release.
			SynthDef(\sspaulstretch1, {arg buf, rate = 1, amp = 1, pan = 0, out = 0,
				                startPos = 0, dur = 1, gate = 1,
				                windowSize = 0.25, overlaps = 4,
				                loop = 0, loopDir = 0, loopStart = 0, loopEnd = 0,
				                releaseMode = 0, releaseStart = 0, releaseEnd = 0,
				                releaseXfade = 0.02;
				var fftSize = 4096;
				var envCtl = \env.kr(Env.newClear(8).asArray);
				var bufDur = BufDur.kr(buf);
				var bufFrames = BufFrames.kr(buf);
				var start = (startPos / bufDur).clip(0, 1);
				// Normalized loop bounds (loopEnd <= 0 -> whole buffer).
				var lEnd = Select.kr(loopEnd > 0, [bufFrames, loopEnd]);
				var lStartNorm = (loopStart / bufFrames).clip(0, 1);
				var lEndNorm = (lEnd / bufFrames).clip(0, 1);
				var lLenNorm = (lEndNorm - lStartNorm).max(0.0001);
				// Pointer speed in normalized buffer-fraction per sample: the
				// whole buffer (1.0) is traversed in `dur` seconds, so the
				// stretch factor baked into `dur` is honored inside the loop
				// region too.
				var ptrSpeed = 1 / (dur * SampleRate.ir);
				var fwd = Phasor.ar(0, ptrSpeed,     lStartNorm, lEndNorm, lStartNorm);
				var rev = Phasor.ar(0, ptrSpeed.neg, lStartNorm, lEndNorm, lEndNorm);
				var tri = Phasor.ar(0, ptrSpeed, 0, 2 * lLenNorm);
				var pal = lStartNorm + (lLenNorm - (tri - lLenNorm).abs);
				var loopPtr = Select.ar(loopDir, [fwd, rev, pal]);
				// One-shot: sweep from the start position to the buffer end over
				// the (already stretched) duration. Held at 1 once it arrives.
				var onePtr = Line.ar(start, 1, dur);
				var ptr = Select.ar(loop, [onePtr, loopPtr]);
				var sustainGrains = Warp1.ar(1, buf, ptr, rate * BufRateScale.kr(buf),
					windowSize, -1, overlaps, 0.1, 2);
				// ---- Release region (Ableton-style), normalized-pointer twin
				// of \ssvoice1. On note-off the pointer crossfades from the
				// sustain reader to a release-region reader over releaseXfade s.
				// releaseMode: 0 off, 1 oneShot, 2 loop (fwd), 3 palin.
				var noteOffTrig = 1 - gate;
				var rEnd = Select.kr(releaseEnd > 0, [bufFrames, releaseEnd]);
				var rStartNorm = (releaseStart / bufFrames).clip(0, 1);
				var rEndNorm = (rEnd / bufFrames).clip(0, 1);
				var rLenNorm = (rEndNorm - rStartNorm).max(0.0001);
				var rFwd = Phasor.ar(noteOffTrig, ptrSpeed,     rStartNorm, rEndNorm, rStartNorm);
				var rTri = Phasor.ar(noteOffTrig, ptrSpeed, 0, 2 * rLenNorm);
				var rPal = rStartNorm + (rLenNorm - (rTri - rLenNorm).abs);
				var rOneShotPtr = (rStartNorm + Sweep.ar(noteOffTrig, ptrSpeed * SampleRate.ir)).min(rEndNorm);
				var releasePtr = Select.ar(releaseMode, [DC.ar(0), rOneShotPtr, rFwd, rPal]);
				var releaseGrains = Warp1.ar(1, buf, releasePtr, rate * BufRateScale.kr(buf),
					windowSize, -1, overlaps, 0.1, 2);
				var releaseActive = releaseMode > 0;
				var fadeAmt = Lag.kr(noteOffTrig * releaseActive, releaseXfade.max(0));
				var sustainGain = cos(fadeAmt * (pi/2));
				var releaseGain = sin(fadeAmt * (pi/2));
				var grains = (sustainGrains * sustainGain) + (releaseGrains * releaseGain);
				// Re-randomize bin phases once per FFT frame (frame period =
				// fftSize * hop samples; hop = 0.5).
				var frameTrig = Impulse.kr(SampleRate.ir / (fftSize * 0.5));
				var chain = FFT(LocalBuf(fftSize), grains, 0.5, 0);
				var sig;
				chain = PV_Diffuser(chain, frameTrig);
				sig = IFFT(chain, 0);
				Out.ar(out, Pan2.ar(sig * EnvGen.kr(envCtl, gate, doneAction: 2) * amp, pan));
			}).add;


			// PaulStretch-style per-section time stretch (stereo).
			// Mirrors \sspaulstretch1 with an independent Warp1 + FFT chain per
			// channel (buf0 = L, buf1 = R) and Balance2 positioning. Pointer
			// timing derives from buf0 (loader allocates L/R as a matched pair).
			SynthDef(\sspaulstretch2, {arg buf0, buf1, rate = 1, amp = 1, pan = 0, out = 0,
				                startPos = 0, dur = 1, gate = 1,
				                windowSize = 0.25, overlaps = 4,
				                loop = 0, loopDir = 0, loopStart = 0, loopEnd = 0,
				                releaseMode = 0, releaseStart = 0, releaseEnd = 0,
				                releaseXfade = 0.02;
				var fftSize = 4096;
				var envCtl = \env.kr(Env.newClear(8).asArray);
				var bufDur = BufDur.kr(buf0);
				var bufFrames = BufFrames.kr(buf0);
				var start = (startPos / bufDur).clip(0, 1);
				var lEnd = Select.kr(loopEnd > 0, [bufFrames, loopEnd]);
				var lStartNorm = (loopStart / bufFrames).clip(0, 1);
				var lEndNorm = (lEnd / bufFrames).clip(0, 1);
				var lLenNorm = (lEndNorm - lStartNorm).max(0.0001);
				var ptrSpeed = 1 / (dur * SampleRate.ir);
				var fwd = Phasor.ar(0, ptrSpeed,     lStartNorm, lEndNorm, lStartNorm);
				var rev = Phasor.ar(0, ptrSpeed.neg, lStartNorm, lEndNorm, lEndNorm);
				var tri = Phasor.ar(0, ptrSpeed, 0, 2 * lLenNorm);
				var pal = lStartNorm + (lLenNorm - (tri - lLenNorm).abs);
				var loopPtr = Select.ar(loopDir, [fwd, rev, pal]);
				var onePtr = Line.ar(start, 1, dur);
				var ptr = Select.ar(loop, [onePtr, loopPtr]);
				// ---- Release region (Ableton-style), shared pointer math.
				var noteOffTrig = 1 - gate;
				var rEnd = Select.kr(releaseEnd > 0, [bufFrames, releaseEnd]);
				var rStartNorm = (releaseStart / bufFrames).clip(0, 1);
				var rEndNorm = (rEnd / bufFrames).clip(0, 1);
				var rLenNorm = (rEndNorm - rStartNorm).max(0.0001);
				var rFwd = Phasor.ar(noteOffTrig, ptrSpeed,     rStartNorm, rEndNorm, rStartNorm);
				var rTri = Phasor.ar(noteOffTrig, ptrSpeed, 0, 2 * rLenNorm);
				var rPal = rStartNorm + (rLenNorm - (rTri - rLenNorm).abs);
				var rOneShotPtr = (rStartNorm + Sweep.ar(noteOffTrig, ptrSpeed * SampleRate.ir)).min(rEndNorm);
				var releasePtr = Select.ar(releaseMode, [DC.ar(0), rOneShotPtr, rFwd, rPal]);
				var releaseActive = releaseMode > 0;
				var fadeAmt = Lag.kr(noteOffTrig * releaseActive, releaseXfade.max(0));
				var sustainGain = cos(fadeAmt * (pi/2));
				var releaseGain = sin(fadeAmt * (pi/2));
				var frameTrig = Impulse.kr(SampleRate.ir / (fftSize * 0.5));
				var grainsL = (Warp1.ar(1, buf0, ptr, rate * BufRateScale.kr(buf0),
					windowSize, -1, overlaps, 0.1, 2) * sustainGain)
					+ (Warp1.ar(1, buf0, releasePtr, rate * BufRateScale.kr(buf0),
						windowSize, -1, overlaps, 0.1, 2) * releaseGain);
				var grainsR = (Warp1.ar(1, buf1, ptr, rate * BufRateScale.kr(buf1),
					windowSize, -1, overlaps, 0.1, 2) * sustainGain)
					+ (Warp1.ar(1, buf1, releasePtr, rate * BufRateScale.kr(buf1),
						windowSize, -1, overlaps, 0.1, 2) * releaseGain);
				var chainL = PV_Diffuser(FFT(LocalBuf(fftSize), grainsL, 0.5, 0), frameTrig);
				var chainR = PV_Diffuser(FFT(LocalBuf(fftSize), grainsR, 0.5, 0), frameTrig);
				var sigL = IFFT(chainL, 0);
				var sigR = IFFT(chainR, 0);
				var env = EnvGen.kr(envCtl, gate, doneAction: 2);
				Out.ar(out, Balance2.ar(sigL * env * amp, sigR * env * amp, pan));
			}).add;
		})
	}

}