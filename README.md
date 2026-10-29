# VidDown (Android) — v0.3.0

লিংক পেস্ট করলে কোয়ালিটির তালিকা দেখায়, কোয়ালিটি বেছে নিলে ভিডিও `Downloads/VidDown` ফোল্ডারে সেভ হয়।
ভেতরে yt-dlp + Python 3.14 + FFmpeg (`viddown-native-bundle` native-v2026.10.28, arm64-v8a)।

## পেজ
নিচে তিনটা বাটন: **Home** (লিংক পেস্ট → কোয়ালিটি → ডাউনলোড), **Library** (`Downloads/VidDown`-এর ফাইল, Open/Delete), **Settings**।
Settings → **Cookie import**: URL bar-এ লিংক দিলে অ্যাপের ভেতরের ব্রাউজারে খোলে, সাইটে লগইন করে ওপরের ডানদিকের **Import** চাপলে কুকি অ্যাপে সেভ হয়; পরের ডাউনলোড/ইনফো রিকোয়েস্টে yt-dlp সেই কুকি ব্যবহার করে।

## APK বানানো

**GitHub Actions (সহজ):** প্রজেক্টটা repo-তে push করো → Actions ট্যাবে "Build APK" চলবে → `VidDown-debug-apk` আর্টিফ্যাক্ট নামিয়ে ফোনে ইনস্টল করো।

**Android Studio:** ফোল্ডারটা Open করো → Run (বা Build > Build APK).

**টার্মিনাল:** JDK 17 + Android SDK থাকলে `gradle :app:assembleDebug`।

## জেনে রাখো
- শুধু arm64-v8a ফোনে চলবে, Android 10+ (minSdk 29)।
- প্রথমবার চালালে Python/yt-dlp আনপ্যাক হয় (একবারই, অ্যাপ আপডেটের পর আবার)।
- FFmpeg বিল্ডটা ছাঁটা: ভিডিও+অডিও জোড়া (remux) চলে, রি-এনকোড/MP3 কনভার্ট চলে না।
- `third_party/` ফোল্ডারে লাইসেন্স ফাইল আছে (FFmpeg LGPL ইত্যাদি)।
