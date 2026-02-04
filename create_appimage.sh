#!/bin/bash

APP_NAME="Osync"
LOWER_APP_NAME="osync"
PROJECT_DIR="composeApp"
ICON_PATH="$PROJECT_DIR/src/jvmMain/resources/icon.png"
DIST_DIR="$PROJECT_DIR/build/compose/binaries/main/app/$APP_NAME"
WORK_DIR="build_appimage"
APP_DIR="$WORK_DIR/$APP_NAME.AppDir"

echo "Let's start building AppImage for $APP_NAME..."

echo "Compiling the project..."
chmod +x gradlew
./gradlew :composeApp:createDistributable

if [ ! -d "$DIST_DIR" ]; then
    echo "Error: Application folder not found: $DIST_DIR"
    exit 1
fi

echo "Preparing folders..."
rm -rf $WORK_DIR
mkdir -p "$APP_DIR/usr/bin"
mkdir -p "$APP_DIR/usr/share/icons/hicolor/256x256/apps"
mkdir -p "$APP_DIR/usr/share/pixmaps"

cp -r "$DIST_DIR"/* "$APP_DIR/usr/bin/"

if [ -f "$ICON_PATH" ]; then
    cp "$ICON_PATH" "$APP_DIR/.DirIcon"
    cp "$ICON_PATH" "$APP_DIR/$LOWER_APP_NAME.png"
    cp "$ICON_PATH" "$APP_DIR/usr/share/icons/hicolor/256x256/apps/$LOWER_APP_NAME.png"
    cp "$ICON_PATH" "$APP_DIR/usr/share/pixmaps/$LOWER_APP_NAME.png"
else
    echo "Icon not found!"
fi

echo "Creating a .desktop..."
cat > "$APP_DIR/$LOWER_APP_NAME.desktop" <<EOF
[Desktop Entry]
Name=$APP_NAME
Exec=$APP_NAME
Icon=$LOWER_APP_NAME
Type=Application
Categories=Utility;Network;
Comment=Osu! Map Synchronizer
Terminal=false
StartupWMClass=$APP_NAME
EOF

echo "Creating AppRun..."
ln -s "usr/bin/bin/$APP_NAME" "$APP_DIR/AppRun"
chmod +x "$APP_DIR/usr/bin/bin/$APP_NAME"

if [ ! -f "appimagetool-x86_64.AppImage" ]; then
    wget -q "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
    chmod +x appimagetool-x86_64.AppImage
fi

echo "Package..."
export ARCH=x86_64
export APPIMAGE_EXTRACT_AND_RUN=1
./appimagetool-x86_64.AppImage "$APP_DIR" "$APP_NAME.AppImage"

echo "Done! Run: ./$APP_NAME.AppImage"