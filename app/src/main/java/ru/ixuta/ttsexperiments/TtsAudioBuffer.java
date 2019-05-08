package ru.ixuta.ttsexperiments;
import ru.ixuta.ttsexperiments.Srt.Entry.LocalizedTextSnippet;

import android.app.Activity;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import static android.speech.tts.TextToSpeech.*;
import static java.lang.System.*;
import static java.nio.ByteOrder.*;

final class TtsAudioBuffer<This extends TtsAudioBuffer> {
	final TtsAudioBuffer t = this;
	final int SAMPLE_RATE = 22050;
	final This.ShortWindow win = new This.ShortWindow(t.SAMPLE_RATE);
	ByteBuffer bBuf = ByteBuffer.allocate(2*1024*1024);

	TextToSpeech tts;
	File file;
	void create(Activity activity) {
		tts = new TextToSpeech(activity, status -> {
			if (status != TextToSpeech.SUCCESS) {
				Log.e("error", "Initilization Failed!");
				return;
			}
			file = new File(activity.getFilesDir(), "user/wav");
			try {
				err.printf("Created new file: %s%n", file.createNewFile());
			} catch (Exception e) {
				err.printf("%s occured%n", e);
			}
		});
	}
	void destroy() {
		tts.shutdown();
	}
	final boolean[] fileIsReady = new boolean[1];
	final UtteranceProgressListener utteranceListener = new UtteranceProgressListener() {
		@Override @SuppressWarnings("deprecation") public void onError(String utteranceId) {}
		@Override public void onStart(String utteranceId) {}
		@Override public void onDone(String utteranceId) {
			if (!utteranceId.isEmpty())
				 return;
			synchronized (fileIsReady) {
				fileIsReady[0] = true;
				fileIsReady.notify();
			}
		}
	};
	void synthesize(Locale locale, String text, float speechRate) {
		try {
			switch (tts.setLanguage(locale)) {
			case LANG_MISSING_DATA:
			case LANG_NOT_SUPPORTED:
				Log.e("error", "This Language is not supported");
				return;
			}
			tts.setSpeechRate(speechRate);
			synchronized (fileIsReady) {
				fileIsReady[0] = false;
				tts.setOnUtteranceProgressListener(utteranceListener);
				tts.synthesizeToFile(text, null, file, "");
				do
					fileIsReady.wait();
				while (!fileIsReady[0]);
			}
			try (var chan = new FileInputStream(file).getChannel()) {
				final var WAV_HEADER_LENGTH=44;
				t.ensureCapacity((int)chan.size()-WAV_HEADER_LENGTH);
				t.bBuf.order(LITTLE_ENDIAN);
				chan.position(WAV_HEADER_LENGTH);
				t.bBuf.clear();
				t.bBuf.limit(chan.read(t.bBuf));
				t.bBuf.rewind();
			}
		} catch (Exception exc) {
			err.printf("%s occured%n", exc);
		}
	}

	void scaleVolume(float scalingFactor) {
		var sb = t.bBuf.asShortBuffer();
		var limit = sb.limit();
		for (var i=0; i<limit; i++)
			sb.put(i, (short)Math.round(sb.get(i) * scalingFactor));
	}

	/* withoutTrimmingSilence & withoutAddingPauses
		void synthesizeFitted(CyclicByteBuffer cb, int millisecs,
		                      List<LocalizedTextSnippet> localizedTextSnippets
		) {
			var requestedLengthInSamples = millisecs /(float) 1000 * t.SAMPLE_RATE;
			var usualLengthInSamples = 0F;
			for (var lts : localizedTextSnippets) {
				t.synthesize(lts.locale, lts.textSnippet, 1F);
				usualLengthInSamples += This.Locales.getWeight(lts.locale) * t.bBuf.remaining() / Short.BYTES;
			}
			var scalingFactorOfWhole = (requestedLengthInSamples / usualLengthInSamples)
				* .9F; //speeding up in excess for insurance
			for (var lts : localizedTextSnippets) {
				t.synthesize(lts.locale, lts.textSnippet,
					This.Locales.scalingFactorToSpeechRate(lts.locale,
						scalingFactorOfWhole * This.Locales.getWeight(lts.locale) ) );
				cb.read(t.bBuf, cb.remaining());
			}
		}
	/*/// trimSilence & addPauses
		void synthesizeFitted(CyclicByteBuffer cb, int millisecs,
		                      List<LocalizedTextSnippet> localizedTextSnippets
		) {
			final var PAUSE_LENGTH_IN_SAMPLES = 712;
			var requestedLengthInSamples = millisecs /(float) 1000 * t.SAMPLE_RATE;
			var usualLengthInSamples = 0F;
			for (var it = localizedTextSnippets.iterator();;) {
				var lts = it.next();
				t.synthesize(lts.locale, lts.textSnippet, 1F);
				t.trimSilence();
				usualLengthInSamples +=
					This.Locales.getWeight(lts.locale) * t.bBuf.remaining() / Short.BYTES;

				usualLengthInSamples += PAUSE_LENGTH_IN_SAMPLES;
				if (!it.hasNext()) {
					//usualLengthInSamples += 3*PAUSE_LENGTH_IN_SAMPLES;
					break;
				}
			}
			var scalingFactorOfWhole = (requestedLengthInSamples / usualLengthInSamples)
				* .9F; //speeding up in excess for insurance
			//err.printf("%6.3f: ", scalingFactorOfWhole);
			for (var it = localizedTextSnippets.iterator();;) {
				var lts = it.next();
				var speechRate = This.Locales.scalingFactorToSpeechRate(
					lts.locale, scalingFactorOfWhole * This.Locales.getWeight(lts.locale)
				);
				//err.printf("%6.3f ", speechRate);
				t.synthesize(lts.locale, lts.textSnippet, speechRate);
				t.trimSilence();
				if (lts.locale == Locale.US)
					t.scaleVolume(.9F);
				cb.read(t.bBuf, cb.remaining());

				cb.readZeros(cb.remaining(), Math.round(scalingFactorOfWhole*PAUSE_LENGTH_IN_SAMPLES)*Short.BYTES);
				if (!it.hasNext()) {
					//cb.readZeros(
					//	cb.remaining(), Math.round(scalingFactorOfWhole*3*PAUSE_LENGTH_IN_SAMPLES)*Short.BYTES
					//);
					break;
				}
			}
			err.println();
		}
	//*/

	static class Locales {
		static final float
			CHINESE_WEIGHT = 0.6F,
			JAPANESE_WEIGHT = CHINESE_WEIGHT,
			ENGLISH_WEIGHT = 1F-CHINESE_WEIGHT;
		static final float[]
			/* withoutTrimmingSilence & withoutAddingPauses
				CHINESE_HYPERBOLA_PARAMETERS =
					{1.7732040F, -0.7458208F, -0.0375369F,  5.0222243F, -4.0082481F, -0.0126590F},
				ENGLISH_HYPERBOLA_PARAMETERS =
					{1.8122062F, -0.7882950F, -0.0318054F,  5.1861847F, -4.1677760F, -0.0136545F};
			/*/// trimSilence & addPauses
				CHINESE_HYPERBOLA_PARAMETERS =
					{1.7715442F, -0.7471100F, -0.0323078F,  5.0377975F, -4.0806848F, +0.0039996F},
				JAPANESE_HYPERBOLA_PARAMETERS =
					{1.7717927F, -0.7500721F, -0.0302079F,  5.0085549F, -4.0256025F, -0.0049099F},
				ENGLISH_HYPERBOLA_PARAMETERS =
					{1.7750670F, -0.7527805F, -0.0301314F,  5.0207221F, -4.0220480F, -0.0080111F};
			//*/

		static float hyperbola(float[] parameters, float x) {
			final var MIN_Y = .2F;
			final var MAX_Y = 4F;
			float y;
			if (x <= 1F) {
				y = parameters[0] + parameters[1] / (x + parameters[2]);
				if (y < MIN_Y) {
					//err.printf("hyperbola: couldn't fit y=%s, setting y=%s instead.%n", y, MIN_Y);
					y = MIN_Y;
				}
			} else {
				y = parameters[3] + parameters[4] / (x + parameters[5]);
				if (y > MAX_Y) {
					err.printf("hyperbola: couldn't fit y=%s, setting y=%s instead.%n", y, MAX_Y);
					y = MAX_Y;
				}
			}
			return y;
		}
		static <This extends Locales>
			float scalingFactorToSpeechRate(Locale locale, float scalingFactor)
		{
			if      (locale==Locale.CHINA)
				return This.hyperbola(CHINESE_HYPERBOLA_PARAMETERS, 1/scalingFactor);
			else if (locale==Locale.JAPAN)
				return This.hyperbola(JAPANESE_HYPERBOLA_PARAMETERS, 1/scalingFactor);
			else if (locale==Locale.US)
				return This.hyperbola(ENGLISH_HYPERBOLA_PARAMETERS, 1/scalingFactor);
			else
				return -1F;
		}
		static float getWeight(Locale locale) {
			if      (locale==Locale.CHINA)
				return CHINESE_WEIGHT;
			else if (locale==Locale.JAPAN)
				return JAPANESE_WEIGHT;
			else if (locale==Locale.US)
				return ENGLISH_WEIGHT;
			else
				return -1F;
		}
	}

	ByteBuffer asByteBuffer() {
		return t.bBuf;
	}

	void truncateFrom(This.Direction dir, int millisecs) {
		final var TRUNCATED_LENGTH_IN_BYTES = Math.round(
			millisecs /(float) 1000 * t.SAMPLE_RATE
		) * Short.BYTES;
		switch (dir) {
		case LEFT:
			t.bBuf.position(t.bBuf.position()+TRUNCATED_LENGTH_IN_BYTES);
			break;
		case RIGHT: default:
			t.bBuf.limit(t.bBuf.limit()-TRUNCATED_LENGTH_IN_BYTES);
		}
		err.printf("truncated %s bytes from %s%n", TRUNCATED_LENGTH_IN_BYTES, dir);
	}

	int milliseconds() {
		var samplesCount = t.bBuf.remaining() /(float) Short.BYTES;
		var samplesPerMillisec = t.SAMPLE_RATE /(float) 1000;
		return Math.round(samplesCount / samplesPerMillisec);
	}

	void ensureCapacity(int minCapacity) {
		if (t.bBuf.capacity() < minCapacity) {
			t.bBuf = ByteBuffer.allocate(minCapacity);
			err.printf("Allocated %.0fK%n", minCapacity /(float) 1024);
		}
	}

	void writeToFile(File file) {
		try (var chan = new FileOutputStream(file).getChannel()) {
			chan.write(t.bBuf);
		} catch (Exception e) {
			err.printf("%s occured%n", e);
		}
	}

	void trimSilence() {
		t.trimSilenceFrom(This.Direction.LEFT);
		t.trimSilenceFrom(This.Direction.RIGHT);
	}

	void trimSilenceFrom(This.Direction dir) {
		final var MAX_NONSILENCE_DURATION = t.SAMPLE_RATE / 20;
		final var THRESHOLD = Short.MAX_VALUE /(float) 1300;

		final var POSITION = t.bBuf.position();
		final var LIMIT = t.bBuf.limit();
		final var REMAINING = t.bBuf.remaining();
		final var INDEX_FROM_DIRECTION = new Object() {
			int calculate(This.Direction dir, int i) {
				switch (dir) {
				case LEFT:
					return POSITION+i;
				case RIGHT: default:
					return (LIMIT-1*Short.BYTES)-i;
				}
			}
		};

		var nonSilDur = 0;
		t.win.clear();
		for (var i=0; i<REMAINING; i+=1*Short.BYTES) {
			t.win.updateSum(t.bBuf.getShort( INDEX_FROM_DIRECTION.calculate(dir,i) ));
			if (t.win.calculateMean() > THRESHOLD) {
				nonSilDur++;
				if (nonSilDur <= MAX_NONSILENCE_DURATION)
					continue;
				i -= (nonSilDur-1)*Short.BYTES;

				switch (dir) {
				case LEFT:
					t.bBuf.position( INDEX_FROM_DIRECTION.calculate(dir,i) );
					break;
				case RIGHT: default:
					t.bBuf.limit( INDEX_FROM_DIRECTION.calculate(dir,i) + 1*Short.BYTES );
				}

				break;
			}
			nonSilDur = 0;
		}
	}

	static class ShortWindow {
		ShortWindow(int sampleRate) {
			this.samples = new short[sampleRate/50];
		}
		final ShortWindow t = this;

		void clear() {
			t.position = 0;
			t.sum = 0;
			Arrays.fill(t.samples, (short)0);
		}
		void updateSum(short sample) {
			t.sum -= t.samples[t.position];
			t.samples[t.position] = (short)Math.abs(sample);
			t.sum += t.samples[t.position];
		
			t.position++;
			if (t.position >= t.samples.length)
				t.position = 0;
		}
		float calculateMean() {
			return t.sum /(float) t.samples.length;
		}

		final short[] samples;
		int position, sum;
	}

	enum Direction {
		LEFT, RIGHT
	}
}
