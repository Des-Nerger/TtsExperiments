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
		var portString = et.getText().toString();
		new Thread(() -> {
			var cb = new CyclicByteBuffer(64*1024*1024);
			cb.bb.order(LITTLE_ENDIAN);

			try (var ss = new ServerSocket(Integer.parseInt(portString), 1)) {
				err.println("Started.");
				for(;;) {
					try (var s = ss.accept();
					     var sc = new Scanner(s.getInputStream());
				  	   var os = s.getOutputStream();
					) {
						var srt = Srt.parse(sc);
						Srt.scale(srt, 2.5F);
						var written=0;
						for (var e : srt) {
							var start = Math.round(
								e.timecodesInMilSecs[0] /(float) 1000 * ttsBuf.SAMPLE_RATE
							) * Short.BYTES;
							{
								var t = Math.round(e.timecodesInMilSecs[0] /(float) 2.5F);
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
					}
					err.println("A client has been disconnected.");
				}
			} catch (Exception exc) {
				//exc.printStackTrace();
				err.printf("%s occured%n", exc);
			}
		}).start();
	}
}
