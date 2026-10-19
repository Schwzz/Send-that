# MediaShare — Real Native Android App

MediaShare is a 100% native Android application built with Kotlin, Jetpack Compose, and Material Design 3.

It enables seamless, direct sharing of actual images and videos from other Android apps without having to manually copy links, launch external downloader tools, or juggle files between folders.

---

## ⚡ Core Workflow

1. **Find Media in Any App**: Open any social media, messaging, or gallery app (Twitter/X, Reddit, Chrome, Gallery).
2. **Tap Android\'s Share Button**: Choose **MediaShare** from the native system share sheet.
3. **Automatic Content Resolution**:
   - For **direct media URLs** (e.g. `.mp4`, `.webm`, `.jpg`, `.png`, `.gif`): MediaShare streams the actual file directly to private storage.
   - For **webpage & social links**: MediaShare inspects the page metadata (`og:video`, `og:image`, `twitter:player:stream`), confirms the direct media endpoint via HTTP HEAD, and downloads the verified stream. If a platform requires user login or blocks automated access (HTTP 401/403), MediaShare displays a clear, honest error with a browser fallback.
   - For **local media content URIs** (`ACTION_SEND` or `ACTION_SEND_MULTIPLE`): MediaShare copies the media stream into private app storage.
4. **Validation & Storage**:
   - Validates file completeness and enforces a 150 MB maximum size limit.
   - Inspects magic bytes (PNG, JPEG, GIF, MP4, WebM) to detect genuine MIME types.
   - Rejects HTML error pages masquerading as media files.
   - Stores files securely in private app storage (`context.filesDir/downloads/`).
5. **Automatic Native Re-Sharing**:
   - Generates a secure, temporary URI via **AndroidX FileProvider**.
   - Automatically opens Android\'s native share sheet (`Intent.createChooser`) with the downloaded media file attached.
   - Saved files remain accessible in MediaShare\'s **Downloads Library** to re-share, open, or delete anytime.

---

## 🔒 Reliability & Security Rules

- **Zero Web / Simulator Code**: Real native Android code using `ComponentActivity`, `Intent`, `ContentResolver`, and `OkHttp`.
- **Path Traversal Protection**: Filenames are strictly sanitized to eliminate directory traversal characters (`..`, `/`, `\`) and invalid filesystem characters.
- **HTML Rejection**: Prevents saving HTML login walls or error pages as corrupt media files.
- **Safe Temporary Files**: Downloads stream to `.part` files; on failure or cancellation, partial files are immediately deleted.
- **Private Storage**: Files are stored exclusively in internal storage (`context.filesDir/downloads/`) and shared securely via `FileProvider`.

---

## 📱 Phone-Only Cloud Build Workflow (No PC Required)

If you are developing entirely on an Android phone without a PC:

### Step 1: Export or Push to GitHub
1. In the AI Studio interface, open the project menu.
2. Select **Push to GitHub** (or **Download ZIP** and upload to a GitHub repository).

### Step 2: Cloud Compilation via GitHub Actions
A complete workflow is provided in `.github/workflows/android.yml`:
1. Open your repository on your phone via mobile browser or the GitHub Android app.
2. Navigate to the **Actions** tab.
3. The workflow runs on every push or can be started manually via **Run workflow**.
4. GitHub\'s cloud runner sets up JDK 17, executes all unit and Robolectric tests, and runs:
   ```bash
   ./gradlew :app:assembleDebug
   ```

### Step 3: Download & Install APK on Your Phone
1. When the run finishes, scroll down to **Artifacts**.
2. Tap **`MediaShare-debug-apk`** to download the ZIP.
3. Open the ZIP using your phone\'s Files app and tap `app-debug.apk` to install MediaShare!

---

## 🧪 Testing

Run the test suite locally with Gradle:
```bash
gradle :app:testDebugUnitTest
```

Build the debug APK locally:
```bash
gradle :app:assembleDebug
```
