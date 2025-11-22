# Classifile

Looks at a directory of filenames, learns their patterns, and then predicts likely next values for each filename 
component. Vibe coded with Claude.

1. **Split filenames into components**

   Given a filename like:

   ```text
   Invoice_2024-03-Report_001.pdf
   ```

   We:

   - Split on separators: space, `.`, `_`, `-`, brackets, commas, etc.
   - Keep the separator characters as their own components (`_`, `-`, `.`, …).
   - For each non-separator token:
     - Split camelCase (`ProjectAlpha` → `Project`, `Alpha`).
     - Split when switching between letters and digits (`File001A` → `File`, `001`, `A`).
   - Detect the extension:
     - Last `.` with 1–5 alphanumeric chars after it is treated as `.` (separator) + `EXT` component.

2. **Classify each component**

   Each component gets a simple type:

   - All digits, length 8 → `:date` (e.g. `20240301`).
   - All digits otherwise → `:numeric`.
   - All letters → `:alpha`.
   - Mixed letters/digits → `:alphanum`.
   - Single separator char we split on → `:sep`.
   - Extension token → `:ext`.

   The filename above becomes something like:

   ```text
   WORD SEP(_) NUM SEP(-) NUM SEP(-) WORD SEP(_) NUM SEP(.) EXT
   ```

3. **Build a “pattern signature”**

   - We turn the sequence of component types into a signature, e.g.:

     ```text
     WORD|SEP(_)|NUM|SEP(-)|NUM|SEP(-)|WORD|SEP(_)|NUM|SEP(.)|EXT
     ```

   - All filenames with the same signature are grouped together into a *pattern group*.

4. **Learn roles at each position**

   Within each pattern group, for each position (0, 1, 2, …) we:

   - Look at all values that appear there across the group.
   - Count distinct values and their frequencies.
   - If the type is `:ext` or `:sep` → role `:constant`.
   - If the type is `:date` → role `:date`.
   - If there is exactly one distinct value → role `:constant`.
   - If type is `:numeric` and the numbers form a reasonably dense range (many values between min and max with few gaps) → role `:index`.
   - Otherwise → role `:unknown`.

   We also infer a simple format:

   - Numeric with fixed width → `%0Nd`, e.g. `"001"` → `%03d`.
   - Date patterns like `yyyyMMdd` or `yyyy-MM-dd`.

5. **Predict next values**

   When you provide a “current” filename:

   - Parse it the same way and find the best-matching pattern group (by comparing component types and constants).
   - For each position:

     - For `:index` (numeric index):
       - Take the max value and suggest `max + 1` (respecting zero-padding).
       - Optionally suggest filling gaps (missing numbers between min and max).
     - For `:constant`:
       - Suggest the constant value.
     - For other positions:
       - Suggest the most frequent values at that position.

   The result is a per-position list of suggestions with simple likelihood scores.

## Scoring System

Suggestions are scored based on their likelihood and purpose:

### For Index Roles (Sequential Numbers)

When a position is recognized as a sequential index (e.g., `001`, `002`, `003`):

- **Next sequential value**: Score `0.9`
  - The most likely next value (current max + 1)
  - Example: If files have `001`, `002`, `003`, then `004` gets score `0.9`

- **Gap filling**: Scores from `0.7` down to `0.3`
  - Missing values in the sequence are suggested to "fill gaps"
  - First gap: `0.7`, second gap: `0.65`, third gap: `0.6`, etc.
  - Minimum score: `0.3` (formula: `max(0.3, 0.7 - 0.05 × gap_position)`)
  - Example: If files have `001`, `002`, `005`, then gap `003` scores `0.7` and gap `004` scores `0.65`

### For Constant and Frequent Value Roles

When a position has constant or varying values:

- **Constants**: Score `1.0`
  - Only one distinct value appears (e.g., file extension `.txt`)

- **Frequent values**: Score = `frequency ratio`
  - Score = (count of this value) / (total count of all values)
  - Example: If position has values `["alpha", "alpha", "beta"]`, then:
    - `"alpha"` scores `0.67` (2/3)
    - `"beta"` scores `0.33` (1/3)
  - Up to 5 most frequent values are suggested

## Running Examples

```bash
# Run the Java interop example (default)
./gradlew run

# Run the Clojure demo
./gradlew runClojureDemo

# Run all tests with detailed output
./gradlew cljTest
```

## Java Interop

Classifile is written in Clojure and can be easily used from Java.

### Data Structures

**Input**: List of filename strings
```java
List<String> filenames = Arrays.asList(
    "File_001.log",
    "File_002.log",
    "File_003.log"
);
```

**Output**: Predictions with the following structure
```java
class ComponentPrediction {
    int position;              // Position index in the filename
    List<Suggestion> suggestions;
}

class Suggestion {
    String value;              // Suggested value (e.g., "004")
    double score;              // Likelihood score (0.0 to 1.0)
    String reason;             // Why this was suggested:
                               //   "next sequential index"
                               //   "fill missing index"
                               //   "constant"
                               //   "frequent value"
}
```

### Usage Example

See `src/main/java/iondrive/classifile/JavaExample.java` for a complete working example.

```java
import iondrive.classifile.JavaExample;
import clojure.lang.IPersistentMap;
import java.util.*;

// 1. Build a model from filenames
List<String> filenames = Arrays.asList(
    "File_001.log",
    "File_002.log",
    "File_003.log"
);
IPersistentMap model = JavaExample.buildModel(filenames);

// 2. Get predictions for next filename
List<JavaExample.ComponentPrediction> predictions =
    JavaExample.getPredictions(model, "File_003.log");

// 3. Extract suggestions
for (JavaExample.ComponentPrediction pred : predictions) {
    System.out.println("Position " + pred.position + ":");
    for (JavaExample.Suggestion sugg : pred.suggestions) {
        System.out.printf("  %s (score: %.2f, reason: %s)%n",
            sugg.value, sugg.score, sugg.reason);
    }
}
```

**Expected output:**
```
Position 0:
  File (score: 1.00, reason: constant)
Position 1:
  _ (score: 1.00, reason: constant)
Position 2:
  004 (score: 0.90, reason: next sequential index)
Position 3:
  . (score: 1.00, reason: constant)
Position 4:
  log (score: 1.00, reason: constant)
```

### Type Reference

**Clojure Types** used in Java:
- `IPersistentMap` - Immutable map (for model and results)
- `IPersistentVector` - Immutable vector (for lists of items)
- `clojure.lang.IFn` - Function reference

**Component Types** (`:type` field):
- `:alpha` - All letters
- `:numeric` - All digits (not 8 digits)
- `:date` - Exactly 8 digits (e.g., `20240301`)
- `:alphanum` - Mixed letters and digits
- `:sep` - Separator character
- `:ext` - File extension

**Component Roles** (`:role` field):
- `:index` - Sequential numeric index
- `:constant` - Always the same value
- `:date` - Date component
- `:unknown` - Variable, non-sequential value

## Publishing to GitHub and Using with JitPack

### Step 1: Prepare for GitHub

1. **Add a version tag to `build.gradle`:**

```gradle
group = 'com.github.yourusername'  // Replace with your GitHub username
version = '1.0.0'
```

2. **Ensure you have a `.gitignore`:**

```
build/
.gradle/
.idea/
*.class
*.log
.DS_Store
```

3. **Commit and push to GitHub:**

```bash
git add .
git commit -m "Prepare for JitPack release"
git push origin master
```

### Step 2: Create a GitHub Release

1. Go to your repository on GitHub
2. Click "Releases" → "Create a new release"
3. Tag version: `v1.0.0` (must start with 'v')
4. Release title: `v1.0.0`
5. Add release notes describing features
6. Click "Publish release"

### Step 3: Build with JitPack

1. Go to [jitpack.io](https://jitpack.io)
2. Enter your repository URL: `https://github.com/yourusername/classifile`
3. Click "Look up"
4. Click "Get it" next to your version tag
5. Wait for JitPack to build (first build may take 2-3 minutes)
6. You'll see a green checkmark when ready

### Step 4: Use in Another Project

Add to your project's `build.gradle`:

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.yourusername:classifile:v1.0.0'
}
```

Or for Maven (`pom.xml`):

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.yourusername</groupId>
        <artifactId>classifile</artifactId>
        <version>v1.0.0</version>
    </dependency>
</dependencies>
```

### Step 5: Verify Installation

Create a test file in your new project:

```java
import iondrive.classifile.JavaExample;
import java.util.*;

public class Test {
    public static void main(String[] args) {
        List<String> files = Arrays.asList("file001.txt", "file002.txt");
        var model = JavaExample.buildModel(files);
        var predictions = JavaExample.getPredictions(model, "file002.txt");
        System.out.println("Classifile working! Predictions: " + predictions.size());
    }
}
```

Run it:
```bash
./gradlew run    # Or your build command
```

### Troubleshooting JitPack

- **Build fails**: Check build logs on JitPack, ensure `build.gradle` has no errors
- **Version not found**: Tag must start with 'v' (e.g., `v1.0.0`)
- **Cache issues**: JitPack caches builds; use a new version tag for changes
- **Gradle version**: JitPack uses Gradle wrapper, ensure `gradlew` is committed

