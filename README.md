# InkNote (Kotlin · Jetpack Compose · Material 3)
- Android Studio (Ladybug+) ile açın, Gradle Sync yapın, tablette/emülatörde çalıştırın. minSdk 26.
- Kalem motoru: Google Ink API (androidx.ink 1.0.0) + MotionEventPredictor.
- PDF: içe aktar (+ "PDF ile aç"), android PdfRenderer ile sayfa render, her sayfaya Ink ile not; veriler cihazda JSON.
- Sürüm hatası olursa app/build.gradle.kts içindeki `ink` sürümünü güncelleyin.
