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

3. **Open in Android Studio:**
   - Launch Android Studio
   - Select "Open" and navigate to the cloned `lemon` folder
   - Wait for Gradle Sync to complete

4. **Build and Run:**
   - Either use Android Studio's **Run** button (green play arrow)
   - Or, build via command line:
     ```bash
     ./gradlew assembleDebug
     ```

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
