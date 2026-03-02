# Lemon - Android Kotlin App

A baseline Android project designed to easily get up and running, running a local LLM inference engine using Jetpack Compose.

## Requirements
- Android Studio (Jellyfish 2023.3.1 or newer recommended for AGP 8.7+)
- JDK 17 or higher
- Android SDK API Level 35

## Local Setup

1. **Clone the repository:**
   ```bash
   git clone <repository_url>
   cd lemon
   ```

2. **Setup Android SDK Path (if not auto-detected):**
   Create a `local.properties` file in the root directory if Android Studio doesn't automatically generate one for you:
   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   ```

3. **Enable USB Debugging on Your Android Device:**
   To deploy the app directly to your phone using `./gradlew installDebug`, ensure your device is prepared:
   - Go to your device's **Settings > About Phone**.
   - Tap **Build Number** 7 times to unlock Developer Options.
   - Navigate back to **Settings > System > Developer Options**.
   - Toggle **USB Debugging** ON.
   - Connect your phone to your computer via USB. Wait for the prompt on your phone and tap **"Allow USB debugging"**.

4. **Open in Android Studio (Optional):**
   - Launch Android Studio
   - Select "Open" and navigate to the cloned `lemon` folder
   - Wait for Gradle Sync to complete

5. **Build and Run:**
   - To build and install the debug APK directly to your connected device from the command line:
     ```bash
     ./gradlew assembleDebug
     ./gradlew installDebug
     ```
   - Alternatively, you can use Android Studio's **Run** button (green play arrow).

## Test the Inference Engine

You can test out the live logic without manual GGUF configuration:
1. Make sure your phone/emulator is connected to WiFi.
2. Open the App and Open the Dropdown on the top-right to click **Download Models**.
3. Choose a verified model from the dynamic Ollama Dropdown UI (e.g. `llama3.2:1b`) and tap Pull.
4. Check your Android device's `Logcat` interface to view chunk downloads completing in real-time.
5. Exit the popup, pick the model from the model-switcher dropdown and begin chatting!

## Contributing
When contributing, ensure you format code based on Kotlin official style guide. Ensure your branch passes the standard `./gradlew assembleDebug` check.

## Using with Antigravity
This project is configured to work seamlessly with the Antigravity AI assistant.

**Getting Started:**
1. Open the project in your IDE where Antigravity is active.
2. Ensure you have the Android SDK installed and configured in `local.properties` (`sdk.dir=/path/to/sdk`).
3. You can ask Antigravity to create new Android components (Activities, Jetpack Compose UI Elements, ViewModels) and it will generate the necessary boilerplate and update the `AndroidManifest.xml` respectively.
4. If you need to add new UI layouts, ask Antigravity to generate or modify Jetpack Compose components instead of XML layout files.

**Tips:**
- To verify builds during an Antigravity session, ask it to run `./gradlew assembleDebug`.
- You can ask Antigravity to explain the current Android project structure if you are new to the codebase.
