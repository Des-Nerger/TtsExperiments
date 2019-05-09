#./gradlew assembleRelease && '/home/ixuta/AndroidStudioProjects/signing/snippets.sh' '/home/ixuta/AndroidStudioProjects/TtsExperiments'
#adb kill-server && adb install -r TtsExperiments.apk
#adb kill-server && adb logcat -c && adb logcat 2>&1 | grep -E '\<(System.(err|out)|E AndroidRuntime)\>'

#nc.openbsd -l 60000 >example_silence-trimmed.raw
#aplay --quiet --file-type raw --channels=1 --format=S16_LE --rate=22050 <example_silence-trimmed.raw

# $ cat /mnt/sdcard/mnttmpfs                                                     
# cd /data/user/0/ru.ixuta.ttsexperiments/files
# #mkdir user
# mount -t tmpfs -o rw,noatime,mode=0771,uid=10065,gid=10065 none $PWD/user

#awk '{printf "%4.2f%10.6f\n", $1, 58053/$2}' android.english.sample4 >android.english.sample4.div
