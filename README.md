# Lemon - Android Kotlin App

A baseline Android project designed to easily get up and running. 

## Requirements
- Android Studio (Jellyfish 2023.3.1 or newer recommended for AGP 8.7+)
- JDK 17 or higher
- Android SDK API Level 34

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
