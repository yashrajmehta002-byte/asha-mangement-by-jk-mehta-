# आशा मैनेजमेंट — Android ऐप (APK) प्रोजेक्ट

ऑफ़लाइन ऐप: डेटा फ़ोन में रहता है, फ़िंगरप्रिंट/स्क्रीन-लॉक से खुलता है, हिंदी/English दोनों।

## APK कैसे बनाएं (सबसे आसान — कंप्यूटर पर कुछ इंस्टॉल नहीं करना)

1. github.com पर मुफ़्त अकाउंट बनाएं → **New repository** → नाम `asha-management` → **Private** चुनें।
2. इस फ़ोल्डर की सारी फ़ाइलें (`.github` फ़ोल्डर समेत) उस repository में अपलोड करें
   (Add file → Upload files; या `git push`)।
3. repository में **Actions** टैब → **Build APK** → **Run workflow**।
4. ~5–8 मिनट बाद रन खोलें → नीचे **Artifacts** में `AshaManagement-apk` डाउनलोड करें → उसके अंदर `AshaManagement.apk`।
5. APK फ़ोन में भेजें और इंस्टॉल करें (फ़ोन "अज्ञात स्रोत से इंस्टॉल" की अनुमति मांगेगा)।

विकल्प: Android Studio में फ़ोल्डर खोलें → Build → Build APK(s)।
कमांड लाइन: `gradle assembleRelease` (Gradle 8.9+, JDK 17, Android SDK 34)।

## ऑटो-अपडेट कैसे चलाएं

1. `app/build.gradle.kts` में `versionCode` बढ़ाएं (1→2→3) और `versionName` बदलें।
2. GitHub पर टैग बनाएं, जैसे `v1.0.1` (Releases → Draft a new release → नया टैग)।
   Actions अपने-आप APK बनाकर **Release** में `AshaManagement.apk` और `version.json` रख देगा।
3. ऐप → सेटिंग → **अपडेट लिंक** में एक बार यह डालें:
   `https://github.com/<आपका-यूज़रनेम>/asha-management/releases/latest/download/version.json`
4. अब ऐप खुलते ही जांचेगा। नया वर्ज़न होने पर ऊपर "अपडेट" बटन आएगा → दबाएं → डाउनलोड → इंस्टॉल।
   पहली बार Android "इस ऐप को इंस्टॉल की अनुमति" मांगेगा; दे दें। डेटा नहीं मिटता।

> **ज़रूरी:** `app/asha-release.jks` (साइनिंग कुंजी) ही हर अपडेट पर इस्तेमाल होती है। इसे बदला/खोया तो नया APK
> पुराने ऐप के ऊपर इंस्टॉल नहीं होगा। इसकी कॉपी सुरक्षित रखें और repository **Private** रखें।
> (पासवर्ड `app/build.gradle.kts` में है; चाहें तो बदलकर नई कुंजी बना सकते हैं, पर तब पुराना ऐप हटाकर इंस्टॉल करना होगा।)

## डेटा सुरक्षा (अनइंस्टॉल / फ़ोन बदलने पर)

* हर बदलाव के बाद ऐप अपने-आप `Documents/AshaManagement/asha_backup.json` में बैकअप रखता है।
* दोबारा इंस्टॉल करने पर: ऐप खोलें → सेटिंग (या पहली स्क्रीन) → **बैकअप से वापस लाएं** → वह फ़ाइल चुनें।
  (Android 10+ में नियम की वजह से नया इंस्टॉल पुरानी फ़ाइल खुद नहीं देख सकता, इसलिए एक बार फ़ाइल चुननी पड़ती है।)
* अगर फ़ोन में Google Backup चालू है, तो ऐप के अंदर रखी बैकअप कॉपी दोबारा इंस्टॉल पर अपने-आप लौट सकती है।
* फ़ोन बदलने/खोने के लिए: सेटिंग → **Drive / WhatsApp पर भेजें** से फ़ाइल Google Drive में सुरक्षित रखें।

## फ़ाइलें

* `app/src/main/assets/www/index.html` — पूरा ऐप (स्क्रीन, लॉजिक, हिंदी/English, रिपोर्ट, नोटिस)
* `app/src/main/java/.../MainActivity.kt` — फ़िंगरप्रिंट लॉक, बैकअप, प्रिंट/PDF, शेयर, SMS, कैमरा, अपडेट
* `.github/workflows/build-apk.yml` — क्लाउड में APK बनाने की फ़ाइल
