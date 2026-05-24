// sectionStretchTest.scd
//
// Audible verification of per-section, pitch-preserving time stretch
// (a technique like PaulStretch) on the voice path.
//
// Each block is meant to be evaluated one at a time. Comments document
// what you should HEAR (the rig is otherwise silent).
//
// Stretch is opt-in: with no stretch arg (or stretch == 1) the voice path
// is byte-identical to gatedLoopTest.scd. With stretch != 1 the voice
// routes through \sspaulstretch1 (mono) / \sspaulstretch2 (stereo):
//   1. Warp1 sweeps the read pointer slowly across the section (granular,
//      pitch held by `rate`), then
//   2. FFT -> PV_Diffuser -> IFFT randomizes bin phases each frame for the
//      smooth PaulStretch spectral smear.
// Pitch never changes with the stretch amount -- only the timebase does.


// ============================================================
// 1. Setup
// ============================================================
(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Tubular/*.aiff").pathMatch;
	t = SSampler(\tubularStretch).load(a, filenameAsKeynum: true);
	"loaded".postln;
};
)

t.keynums;                              // -> detected MIDI keynums per section.


// ============================================================
// 2. Parity baseline -- stretch off
// ============================================================
// No stretch arg: plays through \ssvoice1, perceptually equal to keyVoice.
t.keyVoice(81);                         // HEAR: one-shot note, ~50ms tail.
t.keyVoice(81, stretch: 1);             // HEAR: identical -- 1 == off.


// ============================================================
// 3. Pitch-preserving stretch (the feature)
// ============================================================
// 8x slower. Same pitch as the baseline note above, but spread over ~8x
// the time, with the characteristic PaulStretch smear.
t.keyVoice(81, stretch: 8);

// 30x: an ambient wash. Still the same pitch.
t.keyVoice(81, stretch: 30);

// Compression (factor < 1): plays faster, pitch still unchanged.
t.keyVoice(81, stretch: 0.5);

// A/B the pitch claim: trigger the un-stretched note and a heavily
// stretched one together -- the fundamental should match.
(
t.keyVoice(81);
t.keyVoice(81, stretch: 20, amp: 0.7);
)


// ============================================================
// 4. Window size -- transient detail vs. smear
// ============================================================
// Short window: more grain detail / flutter, transients survive.
t.keyVoice(81, stretch: 20, stretchWindow: 0.05);

// Long window: smoother, more diffuse, glassier.
t.keyVoice(81, stretch: 20, stretchWindow: 0.5);


// ============================================================
// 5. Stretch is independent of pitch transposition
// ============================================================
// `rate` (driven by the played keynum vs. the section's anchor pitch)
// transposes pitch; `stretch` only changes duration. Play the same
// section an octave apart, both stretched 15x.
(
t.keyVoice(t.samples[0].keynum[0]);          // anchor pitch, no stretch
t.keyVoice(t.samples[0].keynum[0] + 12, stretch: 15, amp: 0.7);  // +octave, stretched
)


// ============================================================
// 5b. Loop region under stretch
// ============================================================
// Loop points work at any stretch amount: the pointer wraps inside
// [loopStart, loopEnd] at the stretched speed instead of sweeping the
// whole section once. Release with noteOff (the looped voice is held).
(
~b = t.samples[0].activeBuffer[0][0];   // mono section-0 buffer
~mid = (~b.numFrames * 0.5).asInteger;
~v = t.keyVoice(81, stretch: 12, loop: 1, loopStart: ~mid, loopEnd: ~b.numFrames, note: 81);
)
// HEAR: only the second half of the section, stretched 12x, looping.
t.noteOff(81);

// Reverse and palindrome loop directions under stretch.
~v = t.keyVoice(81, stretch: 12, loop: 1, loopDir: \rev, note: 81);
t.noteOff(81);

~v = t.keyVoice(81, stretch: 12, loop: 1, loopDir: \palin, note: 81);
t.noteOff(81);


// ============================================================
// 6. Per-section override (the per-section control)
// ============================================================
// Bake a stretch into section 0 of the first sample. Any subsequent
// keyVoice / noteOn / Audition for that (sample, section) inherits it,
// without threading `stretch:` through the call site.
(
t.setSampleVoiceArgs(t.samples[0], 0, (
	stretch: 25, stretchWindow: 0.3
));
)
// Now a plain trigger for that section is stretched:
t.playSectionVoice(t.samples[0], 0, note: t.samples[0].keynum[0].asInteger);
// HEAR: 25x stretch even though no stretch arg was passed.

// noteOff releases it (playSectionVoice holds the gate open).
t.noteOff(t.samples[0].keynum[0].asInteger);

// Clear the override -> back to normal playback.
t.clearSampleVoiceArgs(t.samples[0], 0);


// ============================================================
// 7. Stereo dispatch (\sspaulstretch2)
// ============================================================
(
s.waitForBoot{
	g = SSampler(\reelStretch).load([Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Reel Palindruome_44.1kHz_int16.aiff"]);
};
)
~v = g.keyVoice(60, stretch: 12);
~v[0].defName;                          // -> \sspaulstretch2 if the sample is stereo


// ============================================================
// 8. GUI: edit the stretch per section
// ============================================================
// The Voice Editor exposes a "Time stretch (PaulStretch)" Factor / Window
// pair. Set Factor > 1 and click Audition to hear the section stretched.
t.voiceEditor;
