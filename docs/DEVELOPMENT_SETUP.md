# Development Environment Setup — PolyglotMesh

This document records the development environment **as verified on the development machine**.
It is a factual snapshot, not a wish list: every version below was produced by running the
command shown next to it.

- **Verified on:** 15 November 2026
- **Machine:** Windows 11 Home Single Language (build 10.0.26300), AMD64
- **Scope:** PolyglotMesh (frontend + backend toolchain)
- **Nothing was installed or changed during verification.** PATH and environment variable issues
  found below are documented and wait for the project owner's approval.

---

## 1. Verified Tool Matrix

| # | Tool | Required | Found | Status |
|---|---|---|---|---|
| 1 | Java (JDK) 21 | 21.x | 21.0.12 (Oracle JDK) | **Present** — but not the default `java` |
| 2 | GraalVM for JDK 21 | 21.x | Oracle GraalVM 21.0.12+7.1 | **Present** — not on `PATH` |
| 3 | Maven | 3.9.x | 3.9.16 | **Present** — not on `PATH` |
| 4 | Node.js | 20.19+ / 22.12+ | 25.7.0 | **Present** (non-LTS release) |
| 5 | npm | bundled with Node | 11.10.1 | **Present** |
| 6 | Git | any current | 2.53.0.windows.2 | **Present** |

---

## 2. Java 21

Java 21 is installed and is the JDK that `JAVA_HOME` points to.

```powershell
$env:JAVA_HOME
# E:\Program Files\Java

& "$env:JAVA_HOME\bin\java.exe" -version
# java version "21.0.12" 2026-07-21 LTS
# Java(TM) SE Runtime Environment (build 21.0.12+7-LTS-205)
# Java HotSpot(TM) 64-Bit Server VM (build 21.0.12+7-LTS-205, mixed mode, sharing)

& "$env:JAVA_HOME\bin\javac.exe" -version
# javac 21.0.12
```

An **Oracle JDK** 21.0.12 installation, not GraalVM. Its `release` file confirms
`IMPLEMENTOR="Oracle Corporation"` and `JAVA_VERSION="21.0.12"`.

### Issue: the default `java` on PATH is JDK 17, not JDK 21

```powershell
java -version      # openjdk 17.0.19 (Temurin)
javac -version     # javac 17.0.19

where.exe java
# E:\Program Files\Java\JDK-17\bin\java.exe          <-- wins
# C:\Program Files\Common Files\Oracle\Java\javapath\java.exe
```

PATH order puts `E:\Program Files\Java\JDK-17\bin` **before** `%JAVA_HOME%\bin`, so plain
`java`, `javac` and any editor or IDE that resolves `java` from PATH will use **JDK 17**.

`JAVA_HOME` itself is correct, which is why Maven still reports Java 21 (see section 3).

---

## 3. Maven

Maven 3.9.16 is installed but its `bin` directory is **not on PATH**, so `mvn` is not recognised
in a fresh shell. It works correctly when invoked by full path:

```powershell
& "$env:USERPROFILE\.maven\apache-maven-3.9.16\bin\mvn.cmd" -v

# Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
# Maven home: C:\Users\Keerthana\.maven\apache-maven-3.9.16
# Java version: 21.0.12, vendor: Oracle Corporation, runtime: E:\Program Files\Java
# Default locale: en_IN, platform encoding: UTF-8
# OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

Two useful facts from this output:

- Maven already resolves **Java 21** through `JAVA_HOME`, so backend builds will not silently
  compile against JDK 17.
- The local repository `C:\Users\Keerthana\.m2\repository` exists, i.e. Maven has been used
  on this machine before.

`MAVEN_HOME` and `M2_HOME` are not set. Modern Maven does not require them; only the `bin`
directory needs to be on PATH.

---

## 4. GraalVM for JDK 21

Installed at `E:\keerthana\graalvm\graalvm-jdk-21.0.12+7.1`.

```powershell
$env:GRAALVM_HOME = "E:\keerthana\graalvm\graalvm-jdk-21.0.12+7.1"

& "$env:GRAALVM_HOME\bin\java.exe" -version
# java version "21.0.12" 2026-07-21 LTS
# Java(TM) SE Runtime Environment Oracle GraalVM 21.0.12+7.1
#   (build 21.0.12+7-LTS-jvmci-23.1-b96)

& "$env:GRAALVM_HOME\bin\native-image.cmd" --version
# native-image 21.0.12 2026-07-21
# GraalVM Runtime Environment Oracle GraalVM 21.0.12+7.1
```

Its `release` file confirms `GRAALVM_VERSION="23.1.12"` (that is the GraalVM release number,
paired with JDK 21.0.12) and lists the polyglot modules: `org.graalvm.truffle.compiler`,
`org.graalvm.nativeimage`, `jdk.internal.vm.compiler`.

### Notes

- The install is **not on PATH**. `java` still resolves to JDK 17, and no `GRAALVM_HOME` is set.
  Until that changes, Gradle/Maven builds must be given this JDK explicitly, and the backend
  must be launched through `E:\keerthana\graalvm\graalvm-jdk-21.0.12+7.1\bin\java.exe`.
- This is a **JDK-level** GraalVM. It does **not** bundle the JavaScript or Python language
  runtimes: `bin` has no `js`, `python` or `polyglot` launcher. Those are added per-project as
  Maven dependencies (`org.graalvm.polyglot:polyglot`, plus the `js` and `python` language
  artifacts), which is exactly the integration the project plans to use. Nothing is missing —
  this is the expected setup for an embedded Polyglot API application.

**Impact:**

- **Week 1 is unaffected.** Week 1 is frontend-only (React/TypeScript/Vite).
- **Backend execution is unblocked**, once the backend adds the polyglot Maven dependencies and
  runs on this JDK. Verified present; wiring is backend work, not environment work.

**Recommendation (needs approval before acting):** set `GRAALVM_HOME` to this path, add its
`bin` to PATH, and point `JAVA_HOME` at it so one JDK covers both Spring Boot and polyglot
execution. User-level variables only.

---

## 5. Node.js and npm

```powershell
node -v     # v25.7.0
npm -v      # 11.10.1

where.exe node
# E:\Program Files\node.exe
```

Node is installed as a **standalone binary in `E:\Program Files`**, not via the standard
`C:\Program Files\nodejs` location. This works, but note two consequences:

- `npm` resolves to `E:\Program Files\npm.ps1` / `npm.cmd`; global installs land in
  `C:\Users\Keerthana\AppData\Roaming\npm`.
- Node **25.x is an odd-numbered (non-LTS) release**. Vite 7 requires Node 20.19+ or 22.12+,
  so 25.7.0 satisfies the requirement. If the frontend ever hits a Node-related build error,
  switching to the current 22.x or 24.x LTS line is the first thing to try. No change is
  proposed now, because the installed version meets the requirement.

---

## 6. Git

```powershell
git --version    # git version 2.53.0.windows.2
where.exe git
# E:\APS\Git\cmd\git.exe

git config --global user.name     # sthtavarthi-maker
git config --global user.email    # sthtavathi@gmail.com
```

The repository `origin` is `https://github.com/sthtavarthi-maker/PolyglotMesh.git` on branch
`main`. HTTPS push works with the stored credential helper — verified on Day 1.

The GitHub CLI (`gh`) is **not installed**. It is not required: the project uses plain Git.

---

## 7. Additional Tools (informational)

| Tool | Status |
|---|---|
| `yarn`, `pnpm` | Not installed — npm is sufficient, no need to add a second package manager |
| VS Code (`code`) | Installed — `C:\Users\Keerthana\AppData\Local\Programs\Microsoft VS Code\bin\code.cmd` |
| Oracle JDK 25 | Installed — `C:\Program Files\Java\jdk-25` (unrelated to this project) |
| winget | Installed — `C:\Users\Keerthana\AppData\Local\Microsoft\WindowsApps\winget.exe`, available if a tool install is approved |

---

## 8. Known Configuration Issues

These are recorded, not fixed. Fixing any of them changes environment variables, which requires
the project owner's approval.

| # | Issue | Effect | Proposed fix |
|---|---|---|---|
| 1 | Default `java` on PATH is JDK 17 Temurin | IDE terminals, scripts and `java -jar` use 17 while Maven uses 21 — confusing failures, class-file version mismatches | Put `%JAVA_HOME%\bin` **before** `E:\Program Files\Java\JDK-17\bin` in the user PATH |
| 2 | `mvn` not on PATH | Backend cannot be built with a plain `mvn` command | Add `C:\Users\Keerthana\.maven\apache-maven-3.9.16\bin` to the user PATH |
| 3 | Stale PATH entry `C:\Program Files\Java\jdk-21\bin` | Directory does not exist; entry is dead weight | Remove the entry |
| 4 | GraalVM installed but not on PATH; `GRAALVM_HOME` unset | Backend would run on JDK 17 (no polyglot) if launched by a plain `java` command | Set `GRAALVM_HOME`, add its `bin` to PATH, repoint `JAVA_HOME` |

None of these block Week 1.

---

## 9. Re-running the Verification

The full environment can be re-checked with these commands. They are read-only.

```powershell
# Java
java -version; javac -version; $env:JAVA_HOME
& "$env:JAVA_HOME\bin\java.exe" -version

# Maven
& "$env:USERPROFILE\.maven\apache-maven-3.9.16\bin\mvn.cmd" -v

# Frontend
node -v; npm -v

# Version control
git --version; git remote -v; git branch --show-current

# Where each tool actually resolves from
where.exe java; where.exe javac; where.exe node; where.exe git

# GraalVM presence
& "E:\keerthana\graalvm\graalvm-jdk-21.0.12+7.1\bin\java.exe" -version
& "E:\keerthana\graalvm\graalvm-jdk-21.0.12+7.1\bin\native-image.cmd" --version
```

---

## 10. Day 2 Summary

| Requirement | Result |
|---|---|
| Java 21 available | Verified — 21.0.12 at `E:\Program Files\Java` (not the default on PATH) |
| GraalVM JDK 21 available | Verified — Oracle GraalVM 21.0.12+7.1 at `E:\keerthana\graalvm\graalvm-jdk-21.0.12+7.1` (not on PATH) |
| Maven available | Verified — 3.9.16, invoked by full path (not on PATH) |
| Node.js available | Verified — v25.7.0 |
| npm available | Verified — 11.10.1 |
| Git available | Verified — 2.53.0.windows.2, remote reachable |
| No system changes made | Confirmed — verification only |
