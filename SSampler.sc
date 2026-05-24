//Sample Descripter By Allen Wu
//Sampler is dependent on following extentions:
//SCMIR, Make sure you have SCMIR installed in your SuperCollider extensions.  http://composerprogrammer.com/code.html
//wslib Quark


 //instance of Sampler is a database of multiple SampleDescript
SSampler {

	classvar < allSampler;
	classvar <> defaultTexture;
	classvar <> defaultOutputBus = 0;
	classvar <> defaultLoadingServer;

	//Voice-mode (gated) policy. Apply only to noteOn/keyVoice with a note key.
	//\stack: when total active voices reaches maxVoices, drop the new note.
	//\steal: release the oldest held voice (gate=0), then admit the new note.
	classvar <> maxVoices = 16;
	classvar <> voicePolicy = \stack;

	var <dbs;  // an array of SamplerDB instances that this Sampler is registered to.
	var <name;  //Name of this sampler
	var <filenames;
	var <samples;  // samples are SampleDescript instances
	var <bufServer;
	//                                                                |- section -|   |- section -|
	var <keyRanges;// Is a dictionary in format  (SampleDescrtipt -> [[lower, upper], [lower, upper],..], ..)

	//Sampler metadata
	var <numActiveBuffer;
	var <averageDuration;
	var <averageTemporalCentroid;
	var <averageMFCC;

	var <kdTreeNode; // temporarily setting to be [averageDuration, averageTemporalCentroid, averageMFCC].flat

	//Voice-mode registry: note (Integer) -> List of Synth.
	//Populated by #noteOn / #keyVoice, drained on Synth free.
	var <activeVoices;
	//Insertion-ordered flat list of all registered voices, for steal policy.
	var <voiceOrder;

	//Per-(SampleDescript, section) voice-mode overrides.
	//Shape: IdentityDictionary(filename Symbol -> Dictionary(section -> Event)).
	//Keyed by filename (asSymbol) rather than SampleDescript identity so the
	//lookup is robust against any object-identity flake -- Symbols are
	//interned in SC, so the same filename always hashes to the same key.
	//Inner dict is plain Dictionary (`==` comparison) because the section
	//index is an Integer; IdentityDictionary's `===` is unreliable for
	//boxed integers.
	//Consulted by SamplerPrepare#playVoice; takes precedence over args passed
	//to #keyVoice / #noteOn. Has no effect on the concatenative path (#key,
	//#playArgs, #playEnv) -- those don't go through \ssvoice{1,2}.
	var <sampleVoiceArgs;

	// Initialization in this class is in SamplerInstruments.sc
	*initClass {
	}

	*new{arg samplerName, dbname = \default;

		if(allSampler.at(samplerName.asSymbol).isNil)
		{^super.new.init(samplerName, dbname)}
		{^allSampler.at(samplerName.asSymbol)};
	}




	*playArgs{|args|
		args.playSamples = SamplerQuery.getPlayTime(args); // organize play time by peak and stratges

		Routine.run{
			args.playSamples.do{|thisSample, index| //thisSample are realizations of SamplerPrepare class
				var bufRateScale = thisSample.bufServer.sampleRate / thisSample.sample.sampleRate;
				var buf = thisSample.buffer;
				var duration = args.dur ? ((thisSample.sample.activeDuration[thisSample.section]) / thisSample.rate.abs) * bufRateScale; // * (args.expand ? 1)
				var synthID = UniqueID.next.asSymbol;

				thisSample.wait.wait;
				thisSample.play(args, synthID);
				};
			}
	}


	//==============================================================
	//return an array of samplers in the same SamplerDB database
	db{arg samplerName;
		if(samplerName.isNil.not)
		{
			var it = Dictionary.new;
			dbs.do({|samplerDB, index|
				if(samplerDB.samplers.keys.includes(samplerName))
					{
					it = it.put(samplerDB.label, samplerDB.samplers.at(samplerName));
					};
			});

			if (it.isEmpty) {
				Error("This sampler does not exist: " + samplerName).throw;
			};
			^it
		}
		{
			var it = Dictionary.new;
			dbs.do({|samplerDB, index|
				it = it.put(samplerDB.label, samplerDB.samplers);
			});
			if (it.isEmpty) {
				Error("This sampler does not exist: " + name).throw;
			};
			^it
		}
	}


	//=============================
	init{arg samplerName, dbname;
		var database;
		dbs = Dictionary.new;

		if(samplerName.isNil){Error("A sampler name is needed").throw;};

		if(SamplerDB.isLoaded(dbname))
		{
			database = SamplerDB.dbs.at(dbname);
		}
		{
			database = SamplerDB.new(dbname);

		};

		name = samplerName.asSymbol;
		database.put(this);
		dbs.put(dbname.asSymbol, database);
		numActiveBuffer = 0;
		averageDuration = 0;
		averageTemporalCentroid = 0;
		activeVoices = IdentityDictionary.new;
		voiceOrder = List.new;
		sampleVoiceArgs = IdentityDictionary.new;
		allSampler.put(samplerName.asSymbol, this);
	}


	//==============================
	//TODO: Check freeing sampler
	free {
		SamplerDB.dbs[name].removeAt(name);
		samples.do{|thisSample|
			thisSample.free;
		};
		allSampler[name] = nil;
		^super.free;
	}

	//============================
	//load and analyze sound files
	load {arg soundfiles, server = this.class.defaultLoadingServer, filenameAsKeynum = false, normalize = false, startThresh=0.01, endThresh=0.01, override = false, action = nil;
		if(soundfiles.isArray.not){Error("Sound files has to be an array").throw};
		averageMFCC = averageMFCC ? Array.fill(13, 0);
		bufServer = server;
		fork{
			var sample;
			var dict = Dictionary.newFrom([this.filenames, this.samples].flop.flat);
			soundfiles.do{|filename, index|
				if(dict[filename.asSymbol].isNil.not && override.not)
				{
					//"This file is already loaded, reloading".warn;
					//dict[filename.asSymbol].free;
					dict[filename.asSymbol].buffer[0].updateInfo;
					if(dict[filename.asSymbol].buffer[0].numFrames == 0){
						"Can't find Buffer data, reloading....".warn;
						sample = SampleDescript(filename, loadToBuffer: true, filenameAsNote: filenameAsKeynum, normalize: normalize, server: server, action: action);
						dict.put(filename.asSymbol, sample);
					}
					{
						"This file has already loaded.".warn;
					}
				}
				{//load file
					sample = SampleDescript(filename, loadToBuffer: true, filenameAsNote: filenameAsKeynum, normalize: normalize, server: server, action: action);
					numActiveBuffer = numActiveBuffer + sample.activeDuration.size;
					averageDuration = averageDuration + sample.activeDuration.sum;
					averageTemporalCentroid = averageTemporalCentroid + sample.temporalCentroid.sum;
					averageMFCC = averageMFCC + sample.mfcc.sum;
					dict.put(filename.asSymbol, sample);
				};
			};

			averageDuration = averageDuration / numActiveBuffer;
			averageTemporalCentroid = averageTemporalCentroid / numActiveBuffer;
			averageMFCC = averageMFCC / numActiveBuffer;
			dict = dict.asSortedArray.flop;
			filenames = dict[0];
			samples = dict[1];

			kdTreeNode = [averageDuration, averageTemporalCentroid, averageMFCC].flat;

			dbs.do{|thisDB| thisDB.makeTree};

			this.setKeyRanges;
			//finalAction.value;
		}
	}


	//=============================================
	//get anchor keynums for the sample library
	keynums{
		var output = [];
		samples.do{|thisSample, index|
			output = output.add(thisSample.keynum);
		};
		^output;
	}


	//TODO: not Working
	//set anchor keynums arbirurarily
	setKeynums{arg keynumArray, resetKeyRanges = [true, 5];
		keynumArray = keynumArray.asArray;
		samples.do{|thisSample, index|
			var thiskeynum = keynumArray[index].asArray;
			thisSample.keynum.do{|thiskey, idx|
				thisSample.keynum[idx] = thiskeynum[idx] ? thisSample.keynum[idx];
				if(resetKeyRanges[0]){this.setKeyRanges(resetKeyRanges[1])};
			}
		}
	}


	//=================================================================
	//
	setKeyRanges{arg strategy = \keynumRadious, infoArray = [5];
		keyRanges = keyRanges ? Dictionary.new;
		switch(strategy.asSymbol,
			\keynumRadious,{//given a range radious from the keynum of each sample sections.
				samples.do{|thisSample, index|
					var radious = infoArray.asArray.wrapAt(index);
					keyRanges = keyRanges.add(thisSample ->  [(thisSample.keynum - radious).thresh(0), thisSample.keynum + radious].flop)
				};
			},
			\fullRange,{//every sample is responded in full range of midi key number.
				samples.do{|thisSample, index|
					var rangeArray = [];
					thisSample.keynum.size.do{
						rangeArray.add([0, 127]);
					};
					keyRanges = keyRanges.add(thisSample -> rangeArray);
				};
			},
			\keynumOnly,{//only respond to the keynum
				samples.do{|thisSample, index|
					var thisKeynum = thisSample.keynum;
					keyRanges = keyRanges.add(thisSample ->  [thisKeynum, thisKeynum].flop)
				};
			};
		)
	}

	setThresh{|startThresh=0.01, endThresh=0.01, loadToBuffer=true|
		var cond = Condition.new;
		Routine{
			samples.do{|sample|
				sample.freeBuffer;
				sample.arEnv(startThresh, endThresh);
				if(loadToBuffer){sample.loadToBuffer(action: {cond.unhang})};
				cond.hang;
			}
		}.play;
	}



	//========================================
	//Play samples by giving key numbers
	//Defaults are also provided by SamplerArguments
	//Negative key numbers reverses the buffer to play.
	key {arg keynums, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.defaultOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);
		args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));  //find play samples

		if(play){this.playArgs(args)};
		^args;
	}


	setArgs {arg keynums = keynums ? {rrand(10.0, 100.0)}, syncmode = \keeplength, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.defaultOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		args.set(keynums: playkey, syncmode: syncmode, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);

		^args;
	}


	//play samples by giving an array of samples to play
	//the members of samplesArray contains two members: a SampleDescript object, and section index
	// etc. [[SampleDescript, 2], [SampleDescript, 0], ......]
	playSample {arg samplesArray, syncmode = \keeplength, detune = 0, dur = nil, amp = 1, ampenv = [0, 1, 1, 1], pan = 0, panenv = [0, 0, 1, 0], bendenv = nil, texture = defaultTexture, expand = nil, grainRate = 20, grainDur = 0.15, out = this.class.defaultOutputBus, midiChannel = 0, play = true;
		var args = SamplerArguments.new;
		args.set(syncmode: syncmode, detune: detune, dur: dur, amp: amp, ampenv: ampenv, pan: pan, panenv: panenv, bendenv: bendenv, texture: texture, expand: expand, grainRate: grainRate, grainDur: grainDur, out: out, midiChannel: midiChannel);

	}


	// play a SampleArgument object
	playArgs {|args|
		this.class.playArgs(args);
	}




	//==============================================================
	//TODO: Play a sample with the influence of a global envelope
	playEnv {arg env, keynums, dur, amp = 1, pan = 0, maxtexture = 5, out = this.class.defaultOutputBus, midiChannel = 0;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		var argslist= SamplerScore.new;

		case
		// sound is short, repeat it to fill up the envelope
		{(this.averageDuration < 0.3) || ((dur ? 1) < 0.2)}
		{
			Routine.run{
				var elapsed = 0;
				while({elapsed < env.duration},
					{
						var delayTime = 0.03;
						var texture = env.at(elapsed).linlin(0, env.levels.maxItem, 1, maxtexture).asInteger;
						var args = this.key(keynums.asArray.choose, \percussive, dur: dur, amp: env.at(elapsed) * amp, pan: pan, texture: texture, out: out, midiChannel: midiChannel);
						elapsed = elapsed + delayTime;
						argslist.add([args, delayTime]);
						delayTime.wait;
					}
				)
			}
		}
		// for sound with longer duration, put it's peak to each peaks of the envelope
		// reverse the sound to fit the envelope if the attack or release is too short
		{true}
		{
			//For Each Peak time of the envelop, put a sound peaking at that moment
			env.peakTime.do{|thisPeakTime, index|
				var previousPeakTime = env.peakTime[index - 1] ? 0;
				var nextPeakTime = env.peakTime[index + 1] ? env.duration;
				var attackTime = (thisPeakTime - previousPeakTime).abs;
				var releaseTime = (nextPeakTime - thisPeakTime).abs;
				var thisDur = attackTime + releaseTime;
				var args = SamplerArguments.new;
				var ampenv, envStartTime, maxTexture, texture, expand;

				//put data into args
				args.set(keynums: playkey.value.asArray, out: out, midiChannel: midiChannel);
				args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));


				if(attackTime > args.globalAttackDur){
					if(args.globalAttackDur < 0.1){var keys = args.keynums; args.set(keynums: keys ++ keys.neg)};
				};
				if(releaseTime > args.globalReleaseDur){
					if(args.globalReleaseDur < 0.1){var keys = args.keynums; args.set(keynums: keys ++ keys.neg)};
				};

				//if(thisDur > (args.globalDur * 1.6)){expand = thisDur / args.globalDur};

				args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));
				texture = env.range.at(thisPeakTime).linlin(0, 1, 1, maxtexture).asInteger;
				envStartTime = (thisPeakTime-args.globalAttackDur).thresh(0);
				ampenv = env.subEnv(envStartTime, min(args.globalDur, env.duration - thisPeakTime + envStartTime));
				//args.set(syncmode: [\peakat, thisPeakTime], amp: amp, ampenv: ampenv, pan: pan, texture: texture);
				args.set(syncmode: [\peakat, thisPeakTime], amp: env.at(thisPeakTime) * amp, pan: pan, texture: texture, expand: expand);
				this.playArgs(args);
				argslist.add([args, 0])
			}
		};
		^argslist;
	}


	//==============================================================
	// Voice-mode (gated keyboard) API
	//
	// Parallel to #key but routes through \ssvoice1 with a gated lifetime.
	// Bypasses SamplerQuery.getPlayTime entirely -- no concatenative sorting,
	// no texture layering by peak time. SamplerQuery.playing is still
	// populated for parity with the existing introspection surface.
	//
	// Phase 1 step 1: one-shot only (loop is accepted but ignored by the
	// SynthDef until subsequent commits add Phasor pointers).
	//==============================================================

	//Single-call voice trigger. Returns a List of Synths (one per texture layer).
	keyVoice {arg keynums, dur = nil, amp = 1, pan = 0, texture = 1,
		out = this.class.defaultOutputBus, midiChannel = 0,
		gate = 1, loop = 0, loopDir = \fwd, loopMode = \trapezoid,
		loopStart = nil, loopEnd = nil, loopXfade = 0.02,
		attack = 0.005, decay = 0.0, sustainLevel = 1, release = 0.05,
		releaseMode = \off, releaseStart = nil, releaseEnd = nil, releaseXfade = 0.02,
		stretch = nil, stretchWindow = 0.25,
		note = nil;
		var args = SamplerArguments.new;
		var playkey = keynums ? {rrand(10.0, 100.0)};
		args.set(keynums: playkey, dur: dur, amp: amp, pan: pan, texture: texture,
			out: out, midiChannel: midiChannel,
			gate: gate, loop: loop, loopDir: loopDir, loopMode: loopMode,
			loopStart: loopStart, loopEnd: loopEnd, loopXfade: loopXfade,
			attack: attack, decay: decay, sustainLevel: sustainLevel, release: release,
			releaseMode: releaseMode, releaseStart: releaseStart,
			releaseEnd: releaseEnd, releaseXfade: releaseXfade,
			stretch: stretch, stretchWindow: stretchWindow);
		args.setSamples(SamplerQuery.getSamplesByKeynum(this, args));
		^this.playVoiceArgs(args, note);
	}

	//Play a SamplerArguments object via the voice path.
	//Voice-cap policy is enforced per playSample, so a texture > 1 call cannot
	//exceed maxVoices wholesale.
	playVoiceArgs {|args, note = nil|
		var voices = List.new;
		var capped = false;
		args.playSamples.do{|samplePrep|
			var synthID, synth;
			if(note.isNil.not and: { voiceOrder.size >= this.class.maxVoices }) {
				switch(this.class.voicePolicy,
					\steal, { this.stealOldest },
					\stack, { capped = true }
				);
			};
			if(capped.not) {
				synthID = UniqueID.next.asSymbol;
				synth = samplePrep.playVoice(args, synthID);
				if(note.isNil.not) { this.registerVoice(note, synth) };
				voices.add(synth);
			};
		};
		^voices;
	}

	registerVoice {|note, synth|
		var key = note.asInteger;
		var list = activeVoices.at(key);
		if(list.isNil) { list = List.new; activeVoices.put(key, list) };
		list.add(synth);
		voiceOrder.add(synth);
		synth.onFree({ this.unregisterVoice(key, synth) });
	}

	unregisterVoice {|note, synth|
		var key = note.asInteger;
		var list = activeVoices.at(key);
		voiceOrder.remove(synth);
		if(list.isNil.not) {
			list.remove(synth);
			if(list.isEmpty) { activeVoices.removeAt(key) };
		};
	}

	//Release the oldest held voice. Eagerly drop it from voiceOrder so a
	//rapid burst of steals does not temporarily exceed the cap by the
	//release tail of stolen voices; activeVoices is still cleaned via
	//the .onFree path.
	stealOldest {
		var victim;
		if(voiceOrder.isEmpty.not) {
			victim = voiceOrder.removeAt(0);
			victim.set(\gate, 0);
		};
	}

	//MIDI-keyboard-style trigger. Holds the voice open via gate=1.
	//Defaults loop=1 so the voice can sustain past the natural sample end.
	//Uses arg-style declaration because |...| arg defaults must be literal.
	noteOn {arg note, vel = 64, amp = nil, loop = 1, loopDir = \fwd, loopMode = \trapezoid,
		loopStart = nil, loopEnd = nil, loopXfade = 0.02,
		attack = 0.005, decay = 0.0, sustainLevel = 1, release = 0.05,
		releaseMode = \off, releaseStart = nil, releaseEnd = nil, releaseXfade = 0.02,
		stretch = nil, stretchWindow = 0.25,
		dur = nil, pan = 0, out = this.class.defaultOutputBus,
		midiChannel = 0, texture = 1;
		var resolvedAmp = amp ? (vel / 127);
		^this.keyVoice(keynums: note, dur: dur, amp: resolvedAmp, pan: pan,
			texture: texture, out: out, midiChannel: midiChannel,
			gate: 1, loop: loop, loopDir: loopDir, loopMode: loopMode,
			loopStart: loopStart, loopEnd: loopEnd, loopXfade: loopXfade,
			attack: attack, decay: decay, sustainLevel: sustainLevel, release: release,
			releaseMode: releaseMode, releaseStart: releaseStart,
			releaseEnd: releaseEnd, releaseXfade: releaseXfade,
			stretch: stretch, stretchWindow: stretchWindow,
			note: note);
	}

	//Release every voice held under the given note. ASR release runs,
	//doneAction:2 frees the node, .onFree handlers clean both registries.
	noteOff {|note|
		var list = activeVoices.at(note.asInteger);
		if(list.isNil.not) {
			list.copy.do{|synth| synth.set(\gate, 0) };
		};
	}

	allNotesOff {
		activeVoices.keys.copy.do{|key| this.noteOff(key) };
	}


	//Direct-section voice trigger. Bypasses SamplerQuery.getSamplesByKeynum
	//-- no key-range resolution, no closest-match fallback. The caller
	//guarantees which (sample, section) plays. Useful for auditioning
	//per-sample overrides where multiple sections share key ranges.
	//
	//Per-sample voice overrides (#setSampleVoiceArgs) still apply -- the
	//override lookup keys on (sample, section), which is exactly what was
	//passed here.
	//
	//If `note` is supplied, the voice is registered in #activeVoices so
	//#noteOff (and the voice-cap policy) work the same way as for #noteOn.
	playSectionVoice {arg sample, section = 0, keynum = nil, vel = 64, amp = nil,
		dur = nil, pan = 0, out = this.class.defaultOutputBus,
		midiChannel = 0, stretch = nil, stretchWindow = 0.25, note = nil;
		var resolvedAmp = amp ? (vel / 127);
		var sectionKey  = sample.keynum[section];
		//Default: play at the section's anchored pitch (rate == 1).
		var triggerKey  = keynum ? sectionKey;
		var keySign     = triggerKey.sign;
		var args        = SamplerArguments.new;
		var prep        = SamplerPrepare.new;

		args.set(keynums: triggerKey, amp: resolvedAmp, dur: dur, pan: pan,
			texture: 1, out: out, midiChannel: midiChannel, gate: 1, loop: 1,
			stretch: stretch, stretchWindow: stretchWindow);

		prep.bufServer   = bufServer;
		prep.sample      = sample;
		prep.samplerName = this.name;
		prep.duration    = args.dur;
		prep.section     = section;
		prep.setRate(2**((triggerKey.abs - sectionKey)/12) * (keySign + 1 - keySign.abs));
		prep.buffer      = sample.activeBuffer[section];
		prep.midiChannel = args.midiChannel;
		args.setSamples([prep]);

		^this.playVoiceArgs(args, note);
	}


	//==============================================================
	// Per-sample voice-mode overrides
	//==============================================================
	// Configure voice-mode args on a per-(SampleDescript, section) basis.
	// Overrides apply only to voices triggered via #keyVoice / #noteOn
	// (not to #key / #playArgs / #playEnv -- those use the concatenative
	// SynthDefs which have no loop / release-region support).
	//
	// Recognised keys (any subset):
	//   loop, loopDir, loopMode, loopStart, loopEnd, loopXfade,
	//   attack, decay, sustainLevel, release,
	//   releaseMode, releaseStart, releaseEnd, releaseXfade,
	//   ampenv  -- an Env. If set, bypasses the ADSR builder and is plugged
	//              directly into the \env control. A release node is added
	//              automatically if the Env doesn't have one and the voice
	//              is gated.
	//
	// Each call merges the supplied keys onto any existing override Event
	// for this (sample, section). To replace wholesale, call
	// #clearSampleVoiceArgs first.
	//Resolve a SampleDescript to its stable filename-Symbol key.
	prSampleKey {|sample|
		if(sample.isNil) { ^nil };
		if(sample.isKindOf(Symbol)) { ^sample };
		if(sample.isKindOf(String)) { ^sample.asSymbol };
		^sample.filename.asSymbol;
	}

	setSampleVoiceArgs {|sample, section = 0, args|
		var bySection, current, key, sampleKey;
		if(sample.isNil) { Error("setSampleVoiceArgs: sample is nil").throw };
		if(args.isKindOf(Dictionary).not) {
			Error("setSampleVoiceArgs: args must be an Event/Dictionary").throw;
		};
		sampleKey = this.prSampleKey(sample);
		key = section.asInteger;
		bySection = sampleVoiceArgs.at(sampleKey);
		if(bySection.isNil) {
			bySection = Dictionary.new;
			sampleVoiceArgs.put(sampleKey, bySection);
		};
		current = bySection.at(key) ?? { Event.new };
		args.keysValuesDo({|k, v| current.put(k, v) });
		bySection.put(key, current);
		^current;
	}

	//Returns the override Event for this (sample, section), or nil.
	getSampleVoiceArgs {|sample, section = 0|
		var bySection, sampleKey;
		if(sample.isNil) { ^nil };
		sampleKey = this.prSampleKey(sample);
		bySection = sampleVoiceArgs.at(sampleKey);
		if(bySection.isNil) { ^nil };
		^bySection.at(section.asInteger);
	}

	//With no args: clear all overrides. With sample: clear that sample.
	//With sample + section: clear that one section's overrides.
	clearSampleVoiceArgs {|sample = nil, section = nil|
		var bySection, sampleKey;
		if(sample.isNil) {
			sampleVoiceArgs = IdentityDictionary.new;
			^this;
		};
		sampleKey = this.prSampleKey(sample);
		if(section.isNil) {
			sampleVoiceArgs.removeAt(sampleKey);
			^this;
		};
		bySection = sampleVoiceArgs.at(sampleKey);
		if(bySection.isNil.not) {
			bySection.removeAt(section.asInteger);
			if(bySection.isEmpty) { sampleVoiceArgs.removeAt(sampleKey) };
		};
	}
}//end of Sampler class


