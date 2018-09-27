package ru.ixuta.ttsexperiments;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import static java.lang.System.*;

final class CyclicByteBuffer<This extends CyclicByteBuffer> {
	int limit, position;
	ByteBuffer bb;

	CyclicByteBuffer(int capacity) {
		bb = ByteBuffer.allocate(capacity);
	}

	int remaining() {
		var cap = bb.capacity();
		if (position <= limit)
			return limit - position;
		else
			return (cap-position) + (limit-0);
	}

	void write(OutputStream os, int len) throws IOException {
		var a = bb.array();
		write(len, (from, to)->{
			os.write(a, from, to-from);
		});
	}

	void write(WritableByteChannel dst, int len) throws IOException {
		write(len, (from, to)->{
			bb.limit(to); bb.position(from);
			dst.write(bb);
		});
	}

	interface IOExceptingBiIntConsumer {
		void accept(int left, int right) throws IOException;
	}

	void write(int len, IOExceptingBiIntConsumer biIntConsumer) throws IOException {
		var cap = bb.capacity();
		var from = position;
		var to = from + len;
		if (to > cap) {
			var carriedTo = to-cap;
			biIntConsumer.accept(from, cap);
			biIntConsumer.accept(0, carriedTo);
			position = carriedTo;
		} else {
			biIntConsumer.accept(from, to);
			position = to;
		}
	}

	void read(ByteBuffer src, int off) {
		read(off, src.remaining(), (from, to)->{
			src.limit(src.position() + to-from);
			bb.limit(to); bb.position(from);
			bb.put(src);
		});
	}

	void readZeros(int off, int len) {
		var a = bb.array();
		read(off, len, (from, to)->{
			Arrays.fill(a, from, to, (byte)0);
		});
	}

	interface BiIntConsumer {
		void accept(int left, int right);
	}

	void read(int off, int len, BiIntConsumer biIntConsumer) {
		if (len < 0) {
			err.printf("read: negative len==%s; ignoring%n", len);
			return;
		}
		var cap = bb.capacity();
		var from = position + off;
		var to = from + len;
		if            ( from<cap   &&  to<cap  ) {
			biIntConsumer.accept(from, to);
			limit = (position <= limit)? Math.max(limit, to) : limit;
		} else {
			var carriedTo = to-cap;
			if          ( from<cap /*&& to>=cap*/) {
				biIntConsumer.accept(from, cap);
				biIntConsumer.accept(0, carriedTo);
				err.println("[x2]");
			} else /*if (from>=cap   && to>=cap  )*/
				biIntConsumer.accept(from-cap, carriedTo);
			limit = (position <= limit)? carriedTo : Math.max(limit, carriedTo);
		}
	}

/*
	void read(ByteBuffer src, int off) {
		var len = src.remaining();
		if (len == 0) return;
		var cap = bb.capacity();
		var from = position + off;
		var to = from + len;
		if            ( from<cap   && to<=cap  ) {
			bb.limit(to); bb.position(from);
			bb.put(src);
			limit = (position <= limit)? Math.max(limit, to) : limit;
		} else {
			if          ( from<cap   &&  to>cap  ) {
				var originalLimit = src.limit();
				src.limit(originalLimit-(to-cap));
				bb.limit(cap); bb.position(from);
				bb.put(src);
				src.limit(originalLimit);
				bb.limit(to-cap); bb.position(0);
				bb.put(src);
			} else   if (from>=cap   &&  to>cap  )   {
				bb.limit(to-cap); bb.position(from-cap);
				bb.put(src);
			}
			limit = (position <= limit)? to-cap : Math.max(limit, to-cap);
		}
	}

	void readZeros(int off, int len) {
		if (len == 0) return;
		var a = bb.array();
		var cap = a.length;
		var from = position + off;
		var to = from + len;
		if            ( from<cap   && to<=cap  ) {
			Arrays.fill(a, from, to, (byte)0);
			limit = (position <= limit)? Math.max(limit, to) : limit;
		} else {
			if          ( from<cap   &&  to>cap  ) {
				Arrays.fill(a, from, cap, (byte)0);
				Arrays.fill(a, 0, to-cap, (byte)0);
			} else   if (from>=cap   &&  to>cap  )   {
				Arrays.fill(a, from-cap, to-cap, (byte)0);
			}
			limit = (position <= limit)? to-cap : Math.max(limit, to-cap);
		}
	}
-----------------------------------------------------------------------------------------
	void readZeros(int off, int len) {
		var arr = bb.array();
		var offRemainder = (position+off)%arr.length;
		if (offRemainder+len > arr.length) {
			Arrays.fill(arr, offRemainder, arr.length, (byte)0);
			offRemainder=0; len-=offRemainder+len-arr.length;
		}
		Arrays.fill(arr, offRemainder, offRemainder+len, (byte)0);
		if (offRemainder+len > limit)
			limit = offRemainder+len;
	}

	void write(WritableByteChannel dst, int bytesToWrite) throws IOException {
		if (position + bytesToWrite > bb.capacity()) {
			bb.limit(bb.capacity());
			bb.position(position);
			bytesToWrite -= dst.write(bb);
			position = 0;
		}
		bb.limit(position + bytesToWrite);
		bb.position(position);
		position += dst.write(bb);
	}

	int remaining() {
		var exceedingLimit = limit + ((limit < position)? bb.capacity() : 0);
		return exceedingLimit - position;
	}
*/
}
