# Scala Native Support for requests-scala

This project now supports Scala Native (0.5.x) using `libcurl` as the backend.

## Prerequisites

To build and run on Windows, you need:
1.  **LLVM/Clang**: Install via `winget install LLVM.LLVM`.
2.  **vcpkg**: Used to manage `libcurl` and `zlib` dependencies.
3.  **Visual Studio Build Tools**: Specifically the C++ workload.

### Setup Dependencies

1.  Create a folder for vcpkg and install `curl` and `zlib`:
    ```powershell
    vcpkg install curl zlib --triplet x64-windows
    ```
2.  The `build.mill` file is currently configured to look for vcpkg at `C:\Users\hp pro\vcpkg-task\vcpkg_installed\x64-windows`. Update the `vcpkgRoot` variable in `build.mill` if your path is different.

## Building and Running

Use the following command to run tests on Native (from a Developer Command Prompt or via `cmd /c` with `vcvars64.bat`):

```powershell
cmd /c 'call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat" && set "LLVM_BIN=C:\Program Files\LLVM\bin" && set "PATH=%LLVM_BIN%;<path_to_vcpkg_bin>;%PATH%" && mill --no-daemon requests.native[2.13.15].test'
```

## Changes Made

1.  **Source Separation**: Moved JVM-specific logic to `src-jvm/` and created `src-native/` for Scala Native logic.
2.  **Libcurl Backend**: Implemented `Platform.makeRequest` using `libcurl` C interop in `src-native`.
3.  **Compatibility Shims**: Provided shims for `java.net.URL`, `java.net.HttpCookie`, and `javax.net.ssl` classes that are missing in the current Scala Native javalib for Windows.
4.  **Windows Test Fix**: Fixed a bug in `ModelTests.scala` related to URI-encoded paths on Windows.

## Implementation Details

- **Callbacks**: Uses `libcurl`'s `WRITEFUNCTION` and `HEADERFUNCTION` to stream data into buffers.
- **Global State**: Currently uses a global buffer for capturing response data (safe in the current single-threaded Scala Native environment on Windows).
- **SSL**: Supported via `libcurl`'s built-in SSL handling.
