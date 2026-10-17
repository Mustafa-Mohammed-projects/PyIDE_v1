# PyIDE — Python IDE for Android (Kotlin + Jetpack Compose + Chaquopy)

Code editor, console, terminal (REPL + shell), pip package manager, web preview.

## Build on GitHub
1. Create a repo and push this folder.
2. Open **Actions → Build APK** (runs automatically on push).
3. Download the **PyIDE-debug-apk** artifact and install it.

## Your splash image
Replace `app/src/main/res/drawable-nodpi/presplash.png` with your own image (shown for 3 seconds).

## Notes
- Python 3.12 runs via Chaquopy. `pip install` downloads Android wheels from
  https://chaquo.com/pypi-13.1 (numpy, opencv-python, pandas, matplotlib, scipy, pillow, scikit-learn...).
  Packages without an Android build (e.g. Kivy, PyQt, TensorFlow) cannot be installed.
- `cv2.imshow` and `plt.show` render inside the Output panel.
- GUI apps: use the **Web** tab (Flask / http.server).
- ABIs: arm64-v8a, x86_64.
