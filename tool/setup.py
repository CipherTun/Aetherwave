"""Run after `flutter create`: sets app name, permissions and background audio."""
import glob, re
m = 'android/app/src/main/AndroidManifest.xml'
x = open(m).read()
x = re.sub(r'android:label="[^"]*"', 'android:label="Aetherwave"', x, count=1)
perms = ('<uses-permission android:name="android.permission.INTERNET"/>'
 '<uses-permission android:name="android.permission.WAKE_LOCK"/>'
 '<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>'
 '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>')
x = x.replace('<application', perms + '<application', 1)
svc = ('<service android:name="com.ryanheise.audioservice.AudioService" android:foregroundServiceType="mediaPlayback" android:exported="true">'
 '<intent-filter><action android:name="android.media.browse.MediaBrowserService"/></intent-filter></service>'
 '<receiver android:name="com.ryanheise.audioservice.MediaButtonReceiver" android:exported="true">'
 '<intent-filter><action android:name="android.intent.action.MEDIA_BUTTON"/></intent-filter></receiver>')
x = x.replace('</application>', svc + '</application>', 1)
open(m, 'w').write(x)
for k in glob.glob('android/app/src/main/kotlin/**/MainActivity.kt', recursive=True):
    open(k, 'w').write('package com.aether.wave\n\nimport com.ryanheise.audioservice.AudioServiceActivity\n\nclass MainActivity : AudioServiceActivity()\n')
# Keep Android launcher activity aligned with applicationId.
main_activity = Path("android/app/src/main/kotlin/com/aether/wave/MainActivity.kt")
main_activity.parent.mkdir(parents=True, exist_ok=True)

for old_activity in Path("android/app/src/main/kotlin").rglob("MainActivity.kt"):
    if old_activity != main_activity:
        old_activity.unlink()

main_activity.write_text(
    "package com.aether.wave\n\n"
    "import com.ryanheise.audioservice.AudioServiceActivity\n\n"
    "class MainActivity : AudioServiceActivity()\n"
)

p = 'ios/Runner/Info.plist'
x = open(p).read()
x = re.sub(r'(<key>CFBundleDisplayName</key>\s*<string>)[^<]*', r'\1Aetherwave', x)
x = x.replace('</dict>\n</plist>', '<key>UIBackgroundModes</key><array><string>audio</string></array>\n</dict>\n</plist>')
open(p, 'w').write(x)

# Production Android baseline for Google Play in 2026.
import pathlib
gradle = pathlib.Path('android/app/build.gradle.kts')
if gradle.exists():
    g = gradle.read_text()
    g = re.sub(r'compileSdk\s*=\s*[^\n]+', 'compileSdk = 36', g)
    g = re.sub(r'targetSdk\s*=\s*[^\n]+', 'targetSdk = 36', g)
    g = g.replace('compileSdk = flutter.compileSdkVersion', 'compileSdk = 36')
    g = g.replace('targetSdk = flutter.targetSdkVersion', 'targetSdk = 36')
    gradle.write_text(g)
