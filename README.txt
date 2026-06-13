# VidDown - Video Downloader App
# Built with Kotlin + Jetpack Compose + yt-dlp (via Chaquopy Python)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
## ✅ yt-dlp এখন ZIP এর ভেতরে আছে!
   আলাদা করে কিছু নামাতে হবে না।
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## STEP 1: Android Studio তে Open করো
1. Android Studio খোলো
2. File → Open → VidDown folder select করো → OK
3. Gradle sync শুরু হবে (5-10 মিনিট, internet লাগবে)
   - Chaquopy Python runtime ডাউনলোড হবে (~150MB)
   - yt-dlp automatically install হবে
   - ffmpeg-kit ডাউনলোড হবে (~40MB)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
## STEP 2: SDK Check করো
File → Project Structure → SDK Location
- Android SDK 34 (API 34) installed থাকা চাই
- JDK 17 থাকা চাই

যদি SDK না থাকে:
Tools → SDK Manager → Android 14 (API 34) install করো

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
## STEP 3: APK Build করো
Build → Build Bundle(s)/APK(s) → Build APK(s)

Debug APK location:
app/build/outputs/apk/debug/app-debug.apk

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
## STEP 4: Phone এ Install করো
APK file টা phone এ পাঠাও → open করো → Install

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
## Features
✅ Quality Selection (144p → 4K)
✅ Audio MP3 Download
✅ Playlist Download
✅ Subtitle Download (SRT)
✅ Real-time Progress Bar
✅ Download Speed Display
✅ Download History
✅ Clipboard Auto-detect (SnapTube-এর মত!)
✅ Dark Mode UI
✅ File Share & Open

## Supported Platforms
YouTube | Instagram | TikTok | Facebook | Twitter/X | SoundCloud | Twitch

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
## সমস্যা হলে:
1. "Gradle Sync Failed" → File → Invalidate Caches → Restart
2. Build error → Android Studio এর Build output দেখো
3. App crash → Logcat এ "YtDlpManager" দিয়ে filter করো
