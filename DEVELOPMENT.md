# MCMetal Development Setup

This document covers local prerequisites and build workflows for the Phase 0 foundation.

## 1. Toolchain prerequisites

## 1.1 Common (all platforms)

- Java 21 (LTS)
- Git
- Internet access for Gradle dependency resolution

## 1.2 macOS (required for native Metal build)

- macOS 13+ (Ventura/Sonoma/Sequoia supported by project policy)
- Xcode and Xcode Command Line Tools
- CMake 3.27+

Verify key tools:

```bash
java -version
cmake --version
xcodebuild -version
```

## 1.3 Linux/Windows notes

- Java build/test tasks run normally.
- Native task execution is intentionally skipped outside macOS.

## 2. Build and test commands

From repository root:

```bash
./gradlew --no-daemon clean build
```

This command runs Java compilation, unit tests, and (on macOS) native configure/build/smoke-check tasks.

### Useful targeted tasks

```bash
./gradlew nativeConfigure nativeBuild nativeTest
./gradlew test
./gradlew publishToMavenLocal
```

## 3. Native module layout

Native sources are in `native/`:

- `native/include/` - JNI/API headers and version constants
- `native/src/` - C++/Objective-C++ JNI entrypoints and Metal stubs
- `native/scripts/build_macos.sh` - helper script for direct CMake/Xcode builds

The produced macOS artifact is:

- `build/native/libminecraft_metal.dylib`

Gradle stages this artifact into mod resources under:

- `natives/macos/libminecraft_metal.dylib`

## 4. CI behavior

GitHub Actions workflow `.github/workflows/ci.yml` runs on `macos-14` and:

1. Builds Java + native artifacts with `./gradlew clean build`
2. Uploads remapped Java jars from `build/libs/`
3. Uploads `build/native/libminecraft_metal.dylib`
