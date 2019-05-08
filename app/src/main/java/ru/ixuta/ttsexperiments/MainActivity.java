package ru.ixuta.ttsexperiments;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

import static java.lang.System.*;
import static java.nio.ByteOrder.*;

public class MainActivity extends Activity {
	EditText et;
	final TtsAudioBuffer<TtsAudioBuffer> ttsBuf = new TtsAudioBuffer<>();
	Socket currentClientSocket;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		et = (EditText)findViewById(R.id.editText);
		ttsBuf.create(this);
	}

	@Override
	protected void onDestroy() {
		ttsBuf.destroy();
	}

	public void onClick(View v) {
		if (currentClientSocket != null) {
			try {
				currentClientSocket.close();
			} catch (Exception exc) {
				exc.printStackTrace();
				err.printf("%s occured when closing Socket%n", exc);
			}
			return;
		}
		var portString = et.getText().toString();
		new Thread(() -> {
			var cb = new CyclicByteBuffer(64*1024*1024);
			cb.bb.order(LITTLE_ENDIAN);

			try (var ss = new ServerSocket(Integer.parseInt(portString), 1)) {
				err.println("Started.");
				for(;;) {
					try (var s = currentClientSocket = ss.accept();
					     var sc = new Scanner(s.getInputStream());
				  	   var os = s.getOutputStream();
					) {
						var srt = Srt.parse(sc);
						/*
						var text = srt.get(0).localizedTextSnippets.get(0).textSnippet;
						try (var ps = new PrintStream(os)) {
							for (var speechRate=.19F; speechRate<=4.01F; speechRate+=.01F) {
								err.println(speechRate);
								ttsBuf.synthesize(Locale.JAPAN, text, speechRate);
								ttsBuf.trimSilence();
								var samplesCount = ttsBuf.bBuf.remaining() / Short.BYTES;
								ps.printf("%4.2f%10s%n", speechRate, samplesCount);
							}
						}
						*/
						//Srt.scale(srt, 2.5F);
						var written=0;
						for (var e : srt) {
							var start = Math.round(
								e.timecodesInMilSecs[0] /(float) 1000 * ttsBuf.SAMPLE_RATE
							) * Short.BYTES;
							{
								//var t = Math.round(e.timecodesInMilSecs[0] /(float) 2.5F);
								var t = e.timecodesInMilSecs[0];
								err.printf("%02d:%02d,%03d ", t/1000/60, t/1000%60, t%1000);
							}
							cb.readZeros(cb.remaining(), start-written-cb.remaining());
							ttsBuf.synthesizeFitted(cb,
								(int)(e.timecodesInMilSecs[1]-e.timecodesInMilSecs[0]),
								e.localizedTextSnippets);
							written += cb.remaining()*3/4;
							cb.write(os, cb.remaining()*3/4);
						}
						cb.write(os, cb.remaining());
					} catch (Exception exc) {
						if (!exc.getMessage().equals("Socket closed")) {
							exc.printStackTrace();
							err.printf("%s occured%n", exc);
						}
						cb.clear();
					}
					err.println("A client has been disconnected.");
				}
			} catch (Exception exc) {
				exc.printStackTrace();
				err.printf("%s occured when closing ServerSocket%n", exc);
			}
		}).start();
	}
}
