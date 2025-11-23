package iondrive.classifile;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Example demonstrating Java interop with Classifile.
 * Shows how to use the library from Java code with proper type handling.
 */
public class JavaExample {

    // Load the Clojure namespace and functions
    static {
        // Require the namespace
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("iondrive.classifile.core"));
    }

    // Get references to the Clojure functions
    private static final IFn buildModelFromNames =
        Clojure.var("iondrive.classifile.core", "build-model-from-names");

    private static final IFn predict =
        Clojure.var("iondrive.classifile.core", "predict");

    private static final IFn parseFilename =
        Clojure.var("iondrive.classifile.core", "parse-filename");

    private static final IFn getPositionSuggestions =
        Clojure.var("iondrive.classifile.core", "get-position-suggestions");

    private static final IFn getAllPositionValues =
        Clojure.var("iondrive.classifile.core", "get-all-position-values");

    private static final IFn getPatternPositions =
        Clojure.var("iondrive.classifile.core", "get-pattern-positions");

    private static final IFn getAllPatterns =
        Clojure.var("iondrive.classifile.core", "get-all-patterns");

    /**
     * Component types - simplified to just two categories.
     */
    public enum ComponentType {
        PATTERN,  // Predictable patterns: sequential numbers, dates
        VALUE     // Everything else: text, non-sequential numbers (ordered by frequency)
    }

    /**
     * A filename component with its current value and suggestions.
     * Perfect for populating combo boxes in a UI.
     *
     * Suggestions are ordered (no scores needed):
     * - PATTERN: Next in sequence, then gaps
     * - VALUE: Most frequent first, alphabetical tiebreaker
     */
    public static class FilenameComponent {
        public final String currentValue;
        public final List<String> suggestions;  // Pre-ordered, most likely first
        public final ComponentType type;
        public final int position;

        public FilenameComponent(String currentValue, List<String> suggestions,
                                ComponentType type, int position) {
            this.currentValue = currentValue;
            this.suggestions = suggestions;
            this.type = type;
            this.position = position;
        }

        @Override
        public String toString() {
            return String.format("Component{current='%s', type=%s, suggestions=%s}",
                currentValue, type, suggestions);
        }
    }

    /**
     * A parsed filename with extension and all meaningful components.
     * Separators are excluded - only VALUE and PATTERN components included.
     */
    public static class ParsedFilename {
        public final String original;
        public final String extension;  // e.g., ".jpg" or empty string
        public final List<FilenameComponent> components;  // Only meaningful components
        private final List<String> separators;  // Internal: separators between components

        public ParsedFilename(String original, String extension,
                             List<FilenameComponent> components,
                             List<String> separators) {
            this.original = original;
            this.extension = extension;
            this.components = components;
            this.separators = separators;
        }

        /**
         * Reconstruct a filename from new component values.
         * Separators and extension from the original are preserved.
         *
         * @param newValues New values for each component (in order)
         * @return Reconstructed filename
         */
        public String reconstructWith(List<String> newValues) {
            if (newValues.size() != components.size()) {
                throw new IllegalArgumentException(
                    "Expected " + components.size() + " values, got " + newValues.size());
            }

            StringBuilder result = new StringBuilder();

            for (int i = 0; i < newValues.size(); i++) {
                result.append(newValues.get(i));
                if (i < separators.size()) {
                    result.append(separators.get(i));
                }
            }

            result.append(extension);
            return result.toString();
        }

        /**
         * Reconstruct using current component values (returns original filename).
         */
        public String reconstruct() {
            List<String> currentValues = new ArrayList<>();
            for (FilenameComponent comp : components) {
                currentValues.add(comp.currentValue);
            }
            return reconstructWith(currentValues);
        }

        @Override
        public String toString() {
            return String.format("ParsedFilename{original='%s', extension='%s', components=%d}",
                original, extension, components.size());
        }
    }

    /**
     * Represents a single suggestion for a filename component.
     */
    public static class Suggestion {
        public final String value;
        public final double score;
        public final String reason;

        public Suggestion(String value, double score, String reason) {
            this.value = value;
            this.score = score;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return String.format("Suggestion{value='%s', score=%.2f, reason='%s'}",
                value, score, reason);
        }
    }

    /**
     * Represents predictions for a single component position.
     */
    public static class ComponentPrediction {
        public final int position;
        public final List<Suggestion> suggestions;

        public ComponentPrediction(int position, List<Suggestion> suggestions) {
            this.position = position;
            this.suggestions = suggestions;
        }

        @Override
        public String toString() {
            return String.format("Position %d: %s", position, suggestions);
        }
    }

    /**
     * Represents a filename component.
     */
    public static class Component {
        public final String value;
        public final String type;  // :alpha, :numeric, :date, :alphanum, :sep, :ext
        public final String role;  // :index, :constant, :date, :unknown
        public final int index;

        public Component(String value, String type, String role, int index) {
            this.value = value;
            this.type = type;
            this.role = role;
            this.index = index;
        }

        @Override
        public String toString() {
            return String.format("Component{value='%s', type=%s, role=%s, index=%d}",
                value, type, role, index);
        }
    }

    /**
     * Parse a filename into components.
     *
     * @param filename The filename to parse
     * @return List of components
     */
    public static List<Component> parseFilenameToComponents(String filename) {
        IPersistentMap result = (IPersistentMap) parseFilename.invoke(filename);
        IPersistentVector components = (IPersistentVector) result.valAt(Clojure.read(":components"));

        List<Component> componentList = new ArrayList<>();
        for (int i = 0; i < components.count(); i++) {
            IPersistentMap comp = (IPersistentMap) components.nth(i);
            componentList.add(new Component(
                (String) comp.valAt(Clojure.read(":value")),
                comp.valAt(Clojure.read(":type")).toString(),
                comp.valAt(Clojure.read(":role")).toString(),
                ((Long) comp.valAt(Clojure.read(":index"))).intValue()
            ));
        }

        return componentList;
    }

    /**
     * Build a model from a list of filenames.
     *
     * @param filenames List of filename strings
     * @return The model as an IPersistentMap (opaque to Java, pass to predict)
     */
    public static IPersistentMap buildModel(List<String> filenames) {
        // Convert Java List to Clojure vector
        IPersistentVector filenameVector =
            (IPersistentVector) Clojure.var("clojure.core", "vec").invoke(filenames);

        return (IPersistentMap) buildModelFromNames.invoke(filenameVector);
    }

    /**
     * Get predictions for the next filename given a model and current filename.
     *
     * @param model The model (from buildModel)
     * @param currentFilename The current filename
     * @return List of predictions per position
     */
    public static List<ComponentPrediction> getPredictions(
            IPersistentMap model,
            String currentFilename) {

        IPersistentMap result = (IPersistentMap) predict.invoke(model, currentFilename);
        IPersistentVector predictions =
            (IPersistentVector) result.valAt(Clojure.read(":component-predictions"));

        List<ComponentPrediction> predictionList = new ArrayList<>();

        for (int i = 0; i < predictions.count(); i++) {
            IPersistentMap pred = (IPersistentMap) predictions.nth(i);
            int position = ((Long) pred.valAt(Clojure.read(":position"))).intValue();
            IPersistentVector suggestions =
                (IPersistentVector) pred.valAt(Clojure.read(":suggestions"));

            List<Suggestion> suggestionList = new ArrayList<>();
            for (int j = 0; j < suggestions.count(); j++) {
                IPersistentMap sugg = (IPersistentMap) suggestions.nth(j);
                suggestionList.add(new Suggestion(
                    (String) sugg.valAt(Clojure.read(":value")),
                    (Double) sugg.valAt(Clojure.read(":score")),
                    (String) sugg.valAt(Clojure.read(":reason"))
                ));
            }

            predictionList.add(new ComponentPrediction(position, suggestionList));
        }

        return predictionList;
    }

    /**
     * Represents a value with its frequency
     */
    public static class ValueWithFrequency {
        public final String value;
        public final int frequency;
        public final double score;

        public ValueWithFrequency(String value, int frequency, double score) {
            this.value = value;
            this.frequency = frequency;
            this.score = score;
        }

        @Override
        public String toString() {
            return String.format("ValueWithFrequency{value='%s', frequency=%d, score=%.2f}",
                value, frequency, score);
        }
    }

    /**
     * Represents metadata about a position
     */
    public static class PositionInfo {
        public final int position;
        public final String type;
        public final String role;
        public final String format;
        public final int valueCount;
        public final List<String> exampleValues;

        public PositionInfo(int position, String type, String role, String format,
                           int valueCount, List<String> exampleValues) {
            this.position = position;
            this.type = type;
            this.role = role;
            this.format = format;
            this.valueCount = valueCount;
            this.exampleValues = exampleValues;
        }

        @Override
        public String toString() {
            return String.format("Position{pos=%d, type=%s, role=%s, valueCount=%d}",
                position, type, role, valueCount);
        }
    }

    /**
     * Represents a pattern group with example files.
     */
    public static class PatternInfo {
        public final String signature;
        public final int fileCount;
        public final List<String> exampleFiles;

        public PatternInfo(String signature, int fileCount, List<String> exampleFiles) {
            this.signature = signature;
            this.fileCount = fileCount;
            this.exampleFiles = exampleFiles;
        }

        @Override
        public String toString() {
            return String.format("Pattern{signature='%s', fileCount=%d}",
                signature, fileCount);
        }
    }

    /**
     * Parse a filename and get components with suggestions in a single call.
     *
     * @param model The model
     * @param currentFilename The filename to parse and get suggestions for
     * @return ParsedFilename with extension and components (each with current value + suggestions)
     */
    public static ParsedFilename parseCurrentFilename(
            IPersistentMap model,
            String currentFilename) {

        // Parse the filename to get components
        IPersistentMap parsed = (IPersistentMap) parseFilename.invoke(currentFilename);
        IPersistentVector parsedComponents =
            (IPersistentVector) parsed.valAt(Clojure.read(":components"));

        // Get predictions for all positions
        List<ComponentPrediction> predictions = getPredictions(model, currentFilename);

        // Build map of position -> suggestions
        java.util.Map<Integer, List<Suggestion>> suggestionsByPosition = new java.util.HashMap<>();
        for (ComponentPrediction pred : predictions) {
            suggestionsByPosition.put(pred.position, pred.suggestions);
        }

        // Get position info to determine roles (PATTERN vs VALUE)
        List<PositionInfo> positionInfos = getPatternPositions(model, currentFilename);
        java.util.Map<Integer, String> rolesByPosition = new java.util.HashMap<>();
        for (PositionInfo info : positionInfos) {
            rolesByPosition.put(info.position, info.role);
        }

        // Extract extension
        String extension = "";
        for (int i = 0; i < parsedComponents.count(); i++) {
            IPersistentMap comp = (IPersistentMap) parsedComponents.nth(i);
            String type = comp.valAt(Clojure.read(":type")).toString();
            if (":ext".equals(type)) {
                String dot = "";
                // Find preceding dot separator
                if (i > 0) {
                    IPersistentMap prevComp = (IPersistentMap) parsedComponents.nth(i - 1);
                    if (":sep".equals(prevComp.valAt(Clojure.read(":type")).toString())) {
                        dot = (String) prevComp.valAt(Clojure.read(":value"));
                    }
                }
                extension = dot + comp.valAt(Clojure.read(":value"));
            }
        }

        // Build FilenameComponent list and capture separators
        List<FilenameComponent> components = new ArrayList<>();
        List<String> separators = new ArrayList<>();
        String lastSeparator = "";

        for (int i = 0; i < parsedComponents.count(); i++) {
            IPersistentMap comp = (IPersistentMap) parsedComponents.nth(i);
            String value = (String) comp.valAt(Clojure.read(":value"));
            String typeStr = comp.valAt(Clojure.read(":type")).toString();

            // Capture separators but don't include them as components
            if (":sep".equals(typeStr)) {
                lastSeparator = value;
                continue;
            }

            // Skip extensions
            if (":ext".equals(typeStr)) {
                continue;
            }

            // Get role from model analysis (not from initial parse)
            String role = rolesByPosition.getOrDefault(i, ":unknown");

            // Determine component type: PATTERN or VALUE
            ComponentType componentType;
            if (":index".equals(role) || ":date".equals(role)) {
                // Sequential numbers or dates = PATTERN
                componentType = ComponentType.PATTERN;
            } else {
                // Everything else = VALUE
                componentType = ComponentType.VALUE;
            }

            // Get and order suggestions
            List<String> suggestionValues = new ArrayList<>();
            List<Suggestion> positionSuggestions = suggestionsByPosition.get(i);

            if (componentType == ComponentType.PATTERN) {
                // PATTERN: Already ordered correctly (next, then gaps)
                if (positionSuggestions != null) {
                    for (Suggestion sugg : positionSuggestions) {
                        suggestionValues.add(sugg.value);
                    }
                }
            } else {
                // VALUE: Order by frequency (already done), then alphabetically
                // Get all values with frequencies and sort
                List<ValueWithFrequency> allValues =
                    getAllPositionValues(model, currentFilename, i);

                // Sort by frequency desc, then alphabetically
                allValues.sort((a, b) -> {
                    if (a.frequency != b.frequency) {
                        return Integer.compare(b.frequency, a.frequency);
                    }
                    return a.value.compareTo(b.value);
                });

                for (ValueWithFrequency val : allValues) {
                    suggestionValues.add(val.value);
                }
            }

            // Ensure current value is always first
            suggestionValues.remove(value);  // Remove if present
            suggestionValues.add(0, value);  // Add at front

            components.add(new FilenameComponent(
                value, suggestionValues, componentType, i));

            // Store separator that follows this component
            if (!lastSeparator.isEmpty()) {
                separators.add(lastSeparator);
                lastSeparator = "";
            }
        }

        return new ParsedFilename(currentFilename, extension, components, separators);
    }

    /**
     * Get suggestions for a specific position (for populating a combo box).
     * Returns at most 'limit' suggestions with score >= minScore.
     *
     * @param model The model
     * @param currentFilename The current/template filename
     * @param position Which position to get suggestions for
     * @param limit Maximum number of suggestions (default: 10)
     * @param minScore Minimum score threshold (default: 0.0)
     * @return List of suggestions for this position only
     */
    public static List<Suggestion> getPositionSuggestions(
            IPersistentMap model,
            String currentFilename,
            int position,
            int limit,
            double minScore) {

        IPersistentMap options = (IPersistentMap) Clojure.var("clojure.core", "hash-map").invoke(
            Clojure.read(":limit"), limit,
            Clojure.read(":min-score"), minScore
        );

        IPersistentVector suggestions = (IPersistentVector)
            getPositionSuggestions.invoke(model, currentFilename, position, options);

        List<Suggestion> suggestionList = new ArrayList<>();
        for (int i = 0; i < suggestions.count(); i++) {
            IPersistentMap sugg = (IPersistentMap) suggestions.nth(i);
            suggestionList.add(new Suggestion(
                (String) sugg.valAt(Clojure.read(":value")),
                (Double) sugg.valAt(Clojure.read(":score")),
                (String) sugg.valAt(Clojure.read(":reason"))
            ));
        }

        return suggestionList;
    }

    /**
     * Get suggestions for a specific position with default limit (10) and minScore (0.0).
     */
    public static List<Suggestion> getPositionSuggestions(
            IPersistentMap model,
            String currentFilename,
            int position) {
        return getPositionSuggestions(model, currentFilename, position, 10, 0.0);
    }

    /**
     * Get all distinct values observed at a position, sorted by frequency.
     * Useful for showing all possible values in a combo box.
     *
     * @param model The model
     * @param currentFilename The current/template filename
     * @param position Which position to query
     * @return List of values with their frequencies
     */
    public static List<ValueWithFrequency> getAllPositionValues(
            IPersistentMap model,
            String currentFilename,
            int position) {

        IPersistentVector values = (IPersistentVector)
            getAllPositionValues.invoke(model, currentFilename, position);

        List<ValueWithFrequency> valueList = new ArrayList<>();
        for (int i = 0; i < values.count(); i++) {
            IPersistentMap val = (IPersistentMap) values.nth(i);
            Object freqObj = val.valAt(Clojure.read(":frequency"));
            int frequency = (freqObj instanceof Long)
                ? ((Long) freqObj).intValue()
                : ((Integer) freqObj).intValue();

            valueList.add(new ValueWithFrequency(
                (String) val.valAt(Clojure.read(":value")),
                frequency,
                (Double) val.valAt(Clojure.read(":score"))
            ));
        }

        return valueList;
    }

    /**
     * Get metadata about all positions in the pattern.
     * Useful for dynamically building a UI with combo boxes for each position.
     *
     * @param model The model
     * @param currentFilename The current/template filename
     * @return List of position metadata
     */
    public static List<PositionInfo> getPatternPositions(
            IPersistentMap model,
            String currentFilename) {

        IPersistentVector positions = (IPersistentVector)
            getPatternPositions.invoke(model, currentFilename);

        List<PositionInfo> positionList = new ArrayList<>();
        for (int i = 0; i < positions.count(); i++) {
            IPersistentMap pos = (IPersistentMap) positions.nth(i);
            IPersistentVector examples = (IPersistentVector)
                pos.valAt(Clojure.read(":example-values"));

            List<String> exampleList = new ArrayList<>();
            for (int j = 0; j < examples.count(); j++) {
                exampleList.add((String) examples.nth(j));
            }

            String format = (String) pos.valAt(Clojure.read(":format"));

            Object valueCountObj = pos.valAt(Clojure.read(":value-count"));
            int valueCount = (valueCountObj instanceof Long)
                ? ((Long) valueCountObj).intValue()
                : ((Integer) valueCountObj).intValue();

            positionList.add(new PositionInfo(
                ((Number) pos.valAt(Clojure.read(":position"))).intValue(),
                pos.valAt(Clojure.read(":type")).toString(),
                pos.valAt(Clojure.read(":role")).toString(),
                format,
                valueCount,
                exampleList
            ));
        }

        return positionList;
    }

    /**
     * Get information about all pattern groups in the model.
     * Useful for letting users select which pattern to use.
     *
     * @param model The model
     * @return List of pattern information
     */
    public static List<PatternInfo> getAllPatterns(IPersistentMap model) {
        IPersistentVector patterns = (IPersistentVector) getAllPatterns.invoke(model);

        List<PatternInfo> patternList = new ArrayList<>();
        for (int i = 0; i < patterns.count(); i++) {
            IPersistentMap pattern = (IPersistentMap) patterns.nth(i);
            IPersistentVector examples = (IPersistentVector)
                pattern.valAt(Clojure.read(":example-files"));

            List<String> exampleList = new ArrayList<>();
            for (int j = 0; j < examples.count(); j++) {
                exampleList.add((String) examples.nth(j));
            }

            Object fileCountObj = pattern.valAt(Clojure.read(":file-count"));
            int fileCount = (fileCountObj instanceof Long)
                ? ((Long) fileCountObj).intValue()
                : ((Integer) fileCountObj).intValue();

            patternList.add(new PatternInfo(
                (String) pattern.valAt(Clojure.read(":signature")),
                fileCount,
                exampleList
            ));
        }

        return patternList;
    }

    /**
     * Example usage demonstrating the complete workflow.
     */
    public static void main(String[] args) {
        System.out.println("=== Classifile Java Interop Example ===\n");

        // Example 1: Simple image sequence
        System.out.println("EXAMPLE 1: Image Files (IMG_001.jpg, IMG_002.jpg, IMG_003.jpg)\n");
        List<String> imageFiles = Arrays.asList("IMG_001.jpg", "IMG_002.jpg", "IMG_003.jpg");
        IPersistentMap imageModel = buildModel(imageFiles);

        ParsedFilename parsed1 = parseCurrentFilename(imageModel, "IMG_003.jpg");
        System.out.println("Parsed: " + parsed1.original);
        System.out.println("Extension: " + parsed1.extension);
        System.out.println("Components:");
        for (FilenameComponent comp : parsed1.components) {
            System.out.println("  - current: \"" + comp.currentValue + "\"");
            System.out.println("    type: " + comp.type);
            System.out.println("    suggestions: " + comp.suggestions);
        }

        // Example 2: Multiple projects with varying frequency
        System.out.println("\nEXAMPLE 2: Project Files (varying frequency)\n");
        List<String> projectFiles = Arrays.asList(
            "Alpha_report.pdf", "Alpha_summary.pdf", "Alpha_notes.pdf",
            "Beta_report.pdf", "Gamma_report.pdf"
        );
        IPersistentMap projectModel = buildModel(projectFiles);

        ParsedFilename parsed2 = parseCurrentFilename(projectModel, "Alpha_report.pdf");
        System.out.println("Parsed: " + parsed2.original);
        System.out.println("Extension: " + parsed2.extension);
        System.out.println("Components:");
        for (FilenameComponent comp : parsed2.components) {
            System.out.println("  Position " + comp.position + ":");
            System.out.println("    current: \"" + comp.currentValue + "\"");
            System.out.println("    type: " + comp.type);
            System.out.println("    suggestions: " + comp.suggestions);
            if (comp.type == ComponentType.VALUE) {
                System.out.println("    (ordered by frequency, then alphabetically)");
            }
        }

        // Example 3: Sequential pattern (PATTERN type)
        System.out.println("\nEXAMPLE 3: Log Files (File_001.log, File_002.log, File_003.log)\n");
        List<String> logFiles = Arrays.asList("File_001.log", "File_002.log", "File_003.log");
        IPersistentMap logModel = buildModel(logFiles);

        ParsedFilename parsed3 = parseCurrentFilename(logModel, "File_003.log");
        System.out.println("Parsed: " + parsed3.original);
        System.out.println("Extension: " + parsed3.extension);
        System.out.println("Components:");
        for (FilenameComponent comp : parsed3.components) {
            System.out.println("  - current: \"" + comp.currentValue + "\"");
            System.out.println("    type: " + comp.type);
            System.out.println("    suggestions: " + comp.suggestions);
            if (comp.type == ComponentType.PATTERN) {
                System.out.println("    (next in sequence, then gaps)");
            }
        }

        // Example 4: Reconstruction
        System.out.println("\nEXAMPLE 4: Filename Reconstruction\n");
        ParsedFilename parsed4 = parseCurrentFilename(imageModel, "IMG_003.jpg");
        System.out.println("Original: " + parsed4.original);

        // Reconstruct with current values (should match original)
        String reconstructed1 = parsed4.reconstruct();
        System.out.println("Reconstructed (current): " + reconstructed1);
        System.out.println("Matches original: " + reconstructed1.equals(parsed4.original));

        // Reconstruct with new values
        List<String> newValues = Arrays.asList("IMG", "004");
        String reconstructed2 = parsed4.reconstructWith(newValues);
        System.out.println("Reconstructed (new): " + reconstructed2);

        System.out.println("\n" + "=".repeat(70) + "\n");

        // 1. Parse a single filename
        System.out.println("1. Parsing filename:");
        String filename = "Report_2024-03-Invoice_001.pdf";
        List<Component> components = parseFilenameToComponents(filename);
        System.out.println("   Filename: " + filename);
        for (Component comp : components) {
            System.out.println("   " + comp);
        }

        // 2. Build a model from multiple filenames
        System.out.println("\n2. Building model from filenames:");
        List<String> filenames = new ArrayList<>();
        filenames.add("File_001.log");
        filenames.add("File_002.log");
        filenames.add("File_003.log");

        for (String name : filenames) {
            System.out.println("   - " + name);
        }

        IPersistentMap model = buildModel(filenames);
        System.out.println("   Model built successfully!");

        // 3. Get predictions for next filename
        System.out.println("\n3. Getting predictions for: File_003.log");
        List<ComponentPrediction> predictions = getPredictions(model, "File_003.log");

        for (ComponentPrediction pred : predictions) {
            System.out.println("   Position " + pred.position + ":");
            for (Suggestion sugg : pred.suggestions) {
                System.out.println("      " + sugg);
            }
        }

        // 4. Find the next sequential index suggestion
        System.out.println("\n4. Predicted next filename components:");
        for (ComponentPrediction pred : predictions) {
            if (!pred.suggestions.isEmpty()) {
                Suggestion best = pred.suggestions.get(0);
                if ("next sequential index".equals(best.reason)) {
                    System.out.println(
                        "   Position " + pred.position + ": " + best.value +
                        " (score: " + best.score + ")");
                }
            }
        }

        // 5. example - get suggestions for a specific position
        System.out.println("\n5. Combo box example - Position 2 (numeric index):");
        List<Suggestion> comboBoxItems = getPositionSuggestions(model, "File_003.log", 2, 5, 0.0);
        for (Suggestion item : comboBoxItems) {
            System.out.println("   - " + item.value + " (" + item.reason + ")");
        }

        // 6. Get all observed values for a position
        System.out.println("\n6. All observed values at position 2:");
        List<ValueWithFrequency> allValues = getAllPositionValues(model, "File_003.log", 2);
        for (ValueWithFrequency val : allValues) {
            System.out.println("   - " + val.value + " (used " + val.frequency + " times)");
        }

        // 7. Get metadata about all positions
        System.out.println("\n7. Pattern structure:");
        List<PositionInfo> positions = getPatternPositions(model, "File_003.log");
        for (PositionInfo pos : positions) {
            System.out.println("   " + pos + ", examples: " + pos.exampleValues);
        }

        // 8. Get all available patterns
        System.out.println("\n8. Available patterns in model:");
        List<PatternInfo> patterns = getAllPatterns(model);
        for (PatternInfo pattern : patterns) {
            System.out.println("   " + pattern);
            System.out.println("      Examples: " + pattern.exampleFiles);
        }
    }
}
