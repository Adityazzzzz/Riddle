# 🪄 Riddle — The Diary of Tom Riddle (Android App)

*“Write on the page with your pen. After a pause, the diary drinks your ink—your words fade into the paper—the page thinks for a moment, and an answer writes itself back in a flowing hand...”*

Welcome to **Riddle**, a native Android diary application designed for tablets with stylus support (like the S-Pen) that recreates the magical Horcrux journal of Tom Riddle. 

---

## ⚡ Magical Features
- **Zero-UI Page**: No screen overlays, no typing keyboards, no digital chat bubbles. A pure fullscreen paper sheet (`#ECEBE6`) dedicated entirely to your handwriting.
- **Ink-Drinking Animation**: 0.9 seconds after lifting your stylus, the ink blurs and dissolves directly into the page.
- **Dynamic Souls (Personas)**: Tap the subtle indicator in the top-right corner to bind the diary's soul to a different character:
  - ✦ **Tom Riddle** (Gold LED) — charming, manipulative, and deeply curious.
  - ✦ **Harry Potter** (Scarlet LED) — warm, friendly, and slightly impulsive.
  - ✦ **Hermione Granger** (Blue LED) — highly logical, warm-hearted, and encyclopedic.
  - ✦ **Ron Weasley** (Orange LED) — informal, Quidditch-obsessed, complaining about homework.
  - ✦ **Normal Diary** (Green LED) — a quiet, reflective, and poetic notebook assistant.
- **Secret Key Configuration**: Tap the screen with **five fingers simultaneously** to conjure the hidden setup panel where you can bind your Gemini API Key.
- **Sub-2s Response times**: Uses a smart **Bounding-Box crop** that shrinks exported OCR images by 90% (~5KB payloads) and executes API queries in parallel with the fade animations.

---

## 🛠️ The Tech Spellbook
- **UI & Layout**: Jetpack Compose (`Canvas` rendering and coordinate-pressure captures).
- **Core Engine**: Direct chunk-streaming requests via Java's `HttpURLConnection` parsed dynamically using regex tokens (no heavy external HTTP clients).
- **Memory Storage**: Local `SharedPreferences` to save your active character and API keys securely on-device.

---

## 📲 How to Install & Play
1. Download the compiled **[riddle-diary.apk](riddle-diary.apk)** file.
2. Tap the APK on your Android device (enable "Install from unknown sources" if prompted).
3. Get a free Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey).
4. Launch the app, enter your API key, tap **Save Key**, and start writing to the diary!

---

*“I can show you. Let me take you back...”*  
**🪄 Made with magic by adityazzzz**
