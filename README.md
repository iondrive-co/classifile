# Classifile

Analyzes filenames, learns patterns, and suggests values for building new filenames. Perfect for UI combo boxes.

## How It Works

1. **Parse filenames** - Split `IMG_001.jpg` into `["IMG", "001"]` + extension `.jpg`
2. **Learn patterns** - Group similar files, detect sequences
3. **Suggest values** - Ordered by likelihood

### Two Component Types

- **PATTERN** - Predictable sequences (e.g., `001`, `002`, `003` → suggests `004`)
- **VALUE** - Everything else (e.g., `Alpha`, `Beta` → suggests most frequent first)

## Quick Start

```java
// Build model from your files
List<String> files = Arrays.asList("IMG_001.jpg", "IMG_002.jpg", "IMG_003.jpg");
IPersistentMap model = JavaExample.buildModel(files);

// Parse a filename
ParsedFilename parsed = JavaExample.parseCurrentFilename(model, "IMG_003.jpg");

// parsed.extension = ".jpg"
// parsed.components[0] = { currentValue: "IMG", suggestions: ["IMG"], type: VALUE }
// parsed.components[1] = { currentValue: "003", suggestions: ["003", "004"], type: PATTERN }

// Build new filename
String next = parsed.reconstructWith(Arrays.asList("IMG", "004"));
// Result: "IMG_004.jpg"
```


## Java API

### Parse and Get Suggestions

```java
import iondrive.classifile.JavaExample;
import iondrive.classifile.JavaExample.*;
import clojure.lang.IPersistentMap;
import java.util.*;

// 1. Build model from your files
List<String> files = Arrays.asList("IMG_001.jpg", "IMG_002.jpg", "IMG_003.jpg");
IPersistentMap model = JavaExample.buildModel(files);

// 2. Parse a filename and get all components with suggestions
ParsedFilename parsed = JavaExample.parseCurrentFilename(model, "IMG_003.jpg");

// 3. Access results
System.out.println("Extension: " + parsed.extension);  // ".jpg"

for (FilenameComponent comp : parsed.components) {
    System.out.println("Current: " + comp.currentValue);    // "IMG" or "003"
    System.out.println("Type: " + comp.type);               // VALUE or PATTERN
    System.out.println("Suggestions: " + comp.suggestions); // Pre-ordered list
}
```

**Output:**
```
Extension: .jpg
Current: IMG
Type: VALUE
Suggestions: [IMG]
Current: 003
Type: PATTERN
Suggestions: [003, 004]
```

### API Reference

```java
// ParsedFilename - Result with components and reconstruction
class ParsedFilename {
    String original;                    // Original filename
    String extension;                   // ".jpg" (with dot)
    List<FilenameComponent> components; // Meaningful parts only

    String reconstructWith(List<String> newValues)  // Build new filename
    String reconstruct()                            // Get original back
}

// FilenameComponent - One part of the filename
class FilenameComponent {
    String currentValue;        // e.g., "IMG" or "003"
    List<String> suggestions;   // Ordered, current value first
    ComponentType type;         // PATTERN or VALUE
}

// ComponentType - Two types
enum ComponentType {
    PATTERN,  // Sequential: 001→002→003 suggests 004
    VALUE     // Other: ordered by frequency, then alphabetically
}
```

### Complete UI Example

```java
// Build model once
IPersistentMap model = JavaExample.buildModel(filenames);

// Parse current filename
ParsedFilename parsed = JavaExample.parseCurrentFilename(model, currentFilename);

// Build UI with combo boxes
List<JComboBox<String>> comboBoxes = new ArrayList<>();

for (FilenameComponent comp : parsed.components) {
    // Create combo box for this component
    JComboBox<String> combo = new JComboBox<>();

    // Populate with suggestions (already ordered, current value first)
    for (String suggestion : comp.suggestions) {
        combo.addItem(suggestion);
    }

    combo.setSelectedIndex(0);
    comboBoxes.add(combo);

    // Optional: Show type in label
    String label = (comp.type == ComponentType.PATTERN) ? "Index" : "Value";
    panel.add(new JLabel(label + ":"), combo);
}

// When user clicks "Save" - reconstruct filename from combo box values
saveButton.addActionListener(e -> {
    List<String> newValues = new ArrayList<>();
    for (JComboBox<String> combo : comboBoxes) {
        newValues.add((String) combo.getSelectedItem());
    }

    String newFilename = parsed.reconstructWith(newValues);
    System.out.println("New filename: " + newFilename);  // e.g., "IMG_004.jpg"
});
```

## Importing with JitPack

1. Go to [jitpack.io](https://jitpack.io)
2. Enter this repository URL: `iondrive-co/classifile
3. Click "Look up"
4. Click "Get it" next to your version tag
5. Wait for JitPack to build (first build may take 2-3 minutes)
6. You'll see a green checkmark when ready

Add to your project's `build.gradle`:

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'iondrive-co:classifile:v0.2.1'
}
```
