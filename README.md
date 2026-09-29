# Visiting Card Scanner — Android

এই project-এর কাজ:

1. Camera দিয়ে visiting card-এর ছবি তোলা
2. Gallery থেকে card image নেওয়া
3. Google ML Kit দিয়ে on-device OCR
4. নাম, ফোন, ইমেইল, কোম্পানি, পদবি ও website/notes আলাদা করা
5. Save to Contacts চাপলে Android-এর native Contacts editor খুলে pre-filled data দেখানো
6. User চাইলে Save/Cancel করতে পারবেন

## Build

Android Studio-তে project folder খুলুন। Gradle sync সম্পন্ন হওয়ার পর:

- Build > Build Bundle(s) / APK(s) > Build APK(s)

APK সাধারণত:
`app/build/outputs/apk/debug/app-debug.apk`

## গুরুত্বপূর্ণ

এই environment-এ Android SDK/Gradle build toolchain নেই, তাই এখানে সরাসরি APK compile করা সম্ভব হয়নি। Source project সম্পূর্ণ দেওয়া হয়েছে।

## ভবিষ্যৎ উন্নতি

- বাংলা/ইংরেজি mixed card parsing আরও উন্নত করা
- এক ছবিতে একাধিক phone/email detect করা
- QR/vCard scan
- duplicate contact detection
- contact group নির্বাচন
- automatic save (বর্তমানে নিরাপত্তার জন্য Android Contacts editor খুলে user confirmation নেয়)


## Version 2 improvements
- বাংলা UI labels
- Optional duplicate-contact warning
- Validation before saving
- Existing OCR + Contacts workflow preserved
