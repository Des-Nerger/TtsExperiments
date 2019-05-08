package ru.ixuta.ttsexperiments;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import static java.lang.Character.*;
//UB//import static java.lang.Character.UnicodeBlock.*;
import static java.lang.System.*;


final class Srt {
	static <This extends Srt> void scale(List<This.Entry> entries, float scalingFactor) {
		for (var e : entries)
			for (var i=0; i<e.timecodesInMilSecs.length; i++)
				e.timecodesInMilSecs[i] = Math.round(e.timecodesInMilSecs[i] * scalingFactor);
	}

	interface ExceptingSupplier<T> {
		T get() throws Exception;
	}

	static <This extends Srt> List<This.Entry> parse(final Scanner sc)
		throws Exception
	{
		final var primaryLocale = ((ExceptingSupplier<Locale>) () -> {
			final var primaryLocaleLine = sc.nextLine();
			switch (primaryLocaleLine) {
			case "日本": case "日":
				return Locale.JAPAN;
			case "中":
				return Locale.CHINA;
			default:
				throw new Exception(String.format("unsupported primary locale: «%s»%n", primaryLocaleLine));
			}
		}).get();
		err.printf("primary locale is set to %s%n", primaryLocale);
		final var entries = new ArrayList<This.Entry>();
		{
			final var curText = new StringBuilder() ;
			This.Entry curEntry = null ;
			sc.skip("\uFEFF?");
			sc.useDelimiter(This.NONEMPTY_LINES_DELIMITER_PATTERN);
			var state = This.State.NUMERIC_COUNTER;
			for (var i=0; sc.hasNext(); ) {
				switch (state) {
				case NUMERIC_COUNTER:
					/*err.println(*/ Integer.parseInt(sc.next()) /*)*/;
					sc.useDelimiter(This.LINES_DELIMITER_PATTERN);
					state = This.State.TIMECODES;
					break;
				case TIMECODES:
					sc.skip(sc.delimiter());
					curEntry = new This.Entry();
					final var m = sc.skip(This.TIMECODES_EXTRACTOR_PATTERN)
					                .match();
					for (var j=0; j<m.groupCount(); j++)
						curEntry.timecodesInMilSecs[j] = (
							This.Entry.Timecode.parseToMilliseconds(m.group(j+1))
						);
					state = This.State.TEXT;
					break;
				case TEXT: default:
					final var textLine = sc.next();
					if (!This.EMPTY_LINE_PATTERN.matcher(textLine).matches()) {
						curText.append(textLine).append("\n");
						if (sc.hasNext())
							break;
					}
					curEntry.localizedTextSnippets = Entry.LocalizedTextSnippets.snip(curText, primaryLocale);
					curText.setLength(0);
					entries.add(curEntry);
					curEntry = null;
					sc.useDelimiter(This.NONEMPTY_LINES_DELIMITER_PATTERN);
					state = This.State.NUMERIC_COUNTER;
					i++;
				}
			}
		}
		return entries;
	}

	static class Entry {
		final long[] timecodesInMilSecs = {-1L, -1L};
		static class Timecode {
			static final Pattern EXTRACTOR_PATTERN=Pattern.compile(
				"^(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})$"
			);
			static long parseToMilliseconds(String string) {
				var m = EXTRACTOR_PATTERN.matcher(string);
				if (m.matches()) {
					var s=0L;
					for (var i=1; i<m.groupCount(); i++)
						s = s*60 + Long.parseLong(m.group(i));
					return s*1000 + Long.parseLong(m.group(m.groupCount()));
				} else
					return -1L;
			}
		}
		static class LocalizedTextSnippet {
			final Locale locale;
			final String textSnippet;
			LocalizedTextSnippet(Locale locale, String textSnippet) {
				this.locale = locale;
				this.textSnippet = textSnippet;
			}
			public String toString() {
				return String.format("%s:[%s]", locale, textSnippet);
			}
		}
		static final class LocalizedInterval<This extends LocalizedInterval> implements Cloneable {
			Locale locale;
			static final int NO_INDEX = -1;
			int position, limit, firstLocalCharIndex=NO_INDEX;
			@Override
			public LocalizedInterval clone() {
				try {
					return (LocalizedInterval)super.clone();
				} catch (CloneNotSupportedException exc) {
					// Cannot happen since This implements the Cloneable interface
					throw new InternalError(exc.toString());
				}
			}
			LocalizedTextSnippet toLocalizedTextSnippet(StringBuilder sb) {
				return new LocalizedTextSnippet(locale, sb.substring(position, limit));
			}
			void updateAfterInsertingIndex(int index) {
				if (index < position)
					position++;
				if (index < limit)
					limit++;
				if (index <= firstLocalCharIndex)
					firstLocalCharIndex++;
			}
			void add(LocalizedInterval addend) {
				if (addend.locale != null) {
					if (locale!=null && locale!=addend.locale) {//paranoid check. TOCLEAN after debugged
						err.printf("this.%s    addend.%s%n", locale, addend.locale);
						throw new IllegalArgumentException("the intervals can't have existing distinct locales");
					}
					locale = addend.locale;
					if (firstLocalCharIndex == NO_INDEX)
						firstLocalCharIndex = addend.firstLocalCharIndex;
				}
				if ((addend.limit-addend.position) == 0) return;
				if (limit < addend.position) {//paranoid check. TOCLEAN after debugged
					err.printf("this.[%s,%s]   addend.[%s,%s]%n", position, limit, addend.position, addend.limit);
					throw new IllegalArgumentException("the intervals can't be interleaved");
				}
				if (limit != addend.position) //paranoid check. TOCLEAN after debugged
					err.printf("this.[%s,%s]   addend.[%s,%s]%n", position, limit, addend.position, addend.limit);
				limit = addend.limit;
			}
		}
		static final class LocalizedTextSnippets {
			static final int WITHIN_WHITESPACE=0, WITHIN_NONWHITESPACE=1;
			static int toggle(int state) {
				return (state == WITHIN_WHITESPACE)? WITHIN_NONWHITESPACE : WITHIN_WHITESPACE;
			}
			static <This extends LocalizedTextSnippets>
			       List<LocalizedTextSnippet> snip(StringBuilder text, Locale primaryLocale)
			{
				final var END_OF_TEXT = -1;
				final var list = new ArrayList<LocalizedTextSnippet>();
				final var currentToken = new LocalizedInterval();
				var currentSnippet = new LocalizedInterval();
				var state = WITHIN_WHITESPACE;
				for (var i=0;; i++) {
					int c;
					//UB//UnicodeBlock ub;
					if      (i <  text.length()) {
						c = text.charAt(i);
						//UB//ub = UnicodeBlock.of(c);
					} else if (i == text.length()) {
						c = END_OF_TEXT;
						//UB//ub = null;
					} else
						break;
					if (state==WITHIN_NONWHITESPACE && isWhitespace(c) ||
					    state==WITHIN_WHITESPACE && !isWhitespace(c) ||
					    c==END_OF_TEXT
					) {
						do {
							if (currentToken.locale == currentSnippet.locale ||
							    currentToken.locale == null ||
							    currentSnippet.locale == null
							) {
								currentSnippet.add(currentToken);
								if (c == END_OF_TEXT) {
									if (currentSnippet.locale == null)
										currentSnippet.locale = primaryLocale;
								} else
									continue;
							}

							if (currentSnippet.locale == Locale.JAPAN &&
								currentSnippet.firstLocalCharIndex != LocalizedInterval.NO_INDEX
							) {
								switch (text.charAt(currentSnippet.firstLocalCharIndex)) {
								case 'っ': case 'ッ':
									break;
								default:
									text.insert(currentSnippet.firstLocalCharIndex, 'っ');
									i++;
									currentSnippet.updateAfterInsertingIndex(currentSnippet.firstLocalCharIndex);
									currentToken.updateAfterInsertingIndex(currentSnippet.firstLocalCharIndex);
								}
							}

							list.add(currentSnippet.toLocalizedTextSnippet(text));
							currentSnippet = currentToken.clone();
						} while (false) ;
						currentToken.locale = null;
						currentToken.firstLocalCharIndex = LocalizedInterval.NO_INDEX;
						currentToken.position = currentToken.limit;
						state = This.toggle(state);
					}
					if (c != END_OF_TEXT) {
						/*UB//
						if (ub == CJK_UNIFIED_IDEOGRAPHS || ub == CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
						    ub == HIRAGANA || ub == KATAKANA ||
						    0xFF10 <= c && c <= 0xFF19 || 0xFF21 <= c && c <= 0xFF3A || 0xFF41 <= c && c <= 0xFF5A
						) {
						*/
						if (0x4E00 <= c && c <= 0x9FFF || 0x3400 <= c && c <= 0x4DBF ||
						    primaryLocale == Locale.JAPAN && (
						    	0x3041 <= c && c <= 0x3096 || 0x30A1 <= c && c <= 0x30FA ||
						    	0xFF10 <= c && c <= 0xFF19 || 0xFF21 <= c && c <= 0xFF3A || 0xFF41 <= c && c <= 0xFF5A
						    )
						) {
							currentToken.locale = primaryLocale;
							if (currentToken.firstLocalCharIndex == LocalizedInterval.NO_INDEX)
								currentToken.firstLocalCharIndex = i;
						} else if ('0' <= c && c <= '9' || 'A' <= c && c <= 'Z' || 'a' <= c && c <= 'z') {
							if (currentToken.locale != primaryLocale)
								currentToken.locale = Locale.US;
							if (currentToken.firstLocalCharIndex == LocalizedInterval.NO_INDEX)
								currentToken.firstLocalCharIndex = i;
						} else {
							if (c == '\n')
								text.setCharAt(i, ' ');
						}
						currentToken.limit++;
					}
				}
				return list;
			}
		}
		List<LocalizedTextSnippet> localizedTextSnippets;
	}

	enum State {
		NUMERIC_COUNTER, TIMECODES, TEXT
	}

	static final Pattern TIMECODES_EXTRACTOR_PATTERN,
	                     LINES_DELIMITER_PATTERN,
	                     NONEMPTY_LINES_DELIMITER_PATTERN,
	                     EMPTY_LINE_PATTERN;
	static {
		final var TIMECODE_FORMAT_STRING = "HH:mm:ss,SSS"; //almost unused; used only for its length
		final var NL = "\r?\n";
		final var EMPTY_LINE = "[ \t]*";

		TIMECODES_EXTRACTOR_PATTERN = Pattern.compile(
			String.format("^(.{%s}) --> (.{%<s})$",
			              TIMECODE_FORMAT_STRING.length()),
			Pattern.MULTILINE
		);
		LINES_DELIMITER_PATTERN = Pattern.compile(NL);
		EMPTY_LINE_PATTERN = Pattern.compile(EMPTY_LINE);
		NONEMPTY_LINES_DELIMITER_PATTERN = Pattern.compile(
			String.format("%s(?:%s%1$s)*", NL, EMPTY_LINE)
		);
	}
}
