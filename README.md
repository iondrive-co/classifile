# Classifile

Analyzes filenames, learns patterns, and suggests values for building new filenames. Perfect for UI combo boxes.

## How It Works

1. **Parse filenames** - Split `IMG_001.jpg` into `["IMG", "001"]` + extension `.jpg`
2. **Learn patterns** - Group similar files, detect sequences
3. **Suggest values** - Ordered by likelihood

### Two Component Types

- **PATTERN** - Sequential numbers or dates (e.g., `001`, `002`, `003` → suggests `004` first when current is max)
- **VALUE** - Everything else (ordered by frequency, then alphabetically)

## Quick Start

### Java

```java
// Build model from your files
List<String> files = Arrays.asList("IMG_001.jpg", "IMG_002.jpg", "IMG_003.jpg");
IPersistentMap model = JavaExample.buildModel(files);

// Parse a filename (Java API still uses filenames, not positions)
ParsedFilename parsed = JavaExample.parseCurrentFilename(model, "IMG_002.jpg");

// parsed.extension = ".jpg"
// parsed.components[0] = { currentValue: "IMG", suggestions: ["IMG"], type: VALUE }
// parsed.components[1] = { currentValue: "002", suggestions: ["002", "003", "001"], type: PATTERN }
//                                                             ^^^^ current first for existing files

// Build new filename
String next = parsed.reconstructWith(Arrays.asList("IMG", "003"));
// Result: "IMG_003.jpg"
```

**Note:** The Java API still uses filename-based input, while the Clojure API uses position-based input.

### Clojure

```clojure
(require '[iondrive.classifile.core :as cf])

;; Build model from filenames
(def files ["IMG_001.jpg" "IMG_002.jpg" "IMG_003.jpg"])
(def model (cf/build-model-from-names files))

;; Get suggestions for position 1 (existing file "IMG_002.jpg")
(cf/predict model 1)
;; => {:pattern {...}
;;     :elements [{:element-index 0, :type :value, :suggestions ["IMG"]}
;;                {:element-index 1, :type :pattern, :suggestions ["002" "003" "001" "004"]}]}
;;                                                                  ^^^^ current first for existing positions

;; Get suggestions for position 3 (next file after the list)
(cf/predict model 3)
;; => {:pattern {...}
;;     :elements [{:element-index 0, :type :value, :suggestions ["IMG"]}
;;                {:element-index 1, :type :pattern, :suggestions ["004" "001" "002" "003"]}]}
;;                                                                  ^^^^ next first for positions beyond list

;; Get suggestions for just element 0 at position 3
(cf/predict model 3 0)
;; => ["IMG"]

;; Get suggestions for just element 1 at position 3
(cf/predict model 3 1)
;; => ["004" "001" "002" "003"]  ; Next value "004" first (position beyond list)
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
    List<String> suggestions;   // Ordered by likelihood
    ComponentType type;         // PATTERN or VALUE
    int elementIndex;           // Index in components list
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

    // Populate with suggestions (already ordered by likelihood)
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
