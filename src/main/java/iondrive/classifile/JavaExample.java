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

    private static final IFn getAllPositionValues =
        Clojure.var("iondrive.classifile.core", "get-all-position-values");

    private static final IFn getPatternPositions =
        Clojure.var("iondrive.classifile.core", "get-pattern-positions");

    private static final IFn getAllPatterns =
        Clojure.var("iondrive.classifile.core", "get-all-patterns");

    private static final IFn getElementSuggestions =
        Clojure.var("iondrive.classifile.core", "get-element-suggestions");

    /**
     * Helper method to repeat a string n times (Java 8 compatible).
     */
    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * Component types - simplified to just two categories.
     */
    public enum ComponentType {
        PATTERN,  // Predictable patterns: sequential numbers, dates
        VALUE     // Everything else: text, non-sequential numbers (ordered by frequency)
    }

    /**
     * A filename component (element) with its current value and suggestions.
     * Perfect for populating combo boxes in a UI.
     *
     * Suggestions are ordered (no scores needed):
     * - PATTERN: Current first (unless current is max, then next first), others alphabetically, next last
     * - VALUE: Current first, others by frequency then alphabetically
     */
    public static class FilenameComponent {
        public final String currentValue;
        public final List<String> suggestions;  // Pre-ordered, most likely first
        public final ComponentType type;
        public final int elementIndex;  // Index in the non-separator components list

        public FilenameComponent(String currentValue, List<String> suggestions,
                                ComponentType type, int elementIndex) {
            this.currentValue = currentValue;
            this.suggestions = suggestions;
            this.type = type;
            this.elementIndex = elementIndex;
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
     * Represents a value with its frequency
     */
    public static class ValueWithFrequency {
        public final String value;
        public final int frequency;

        public ValueWithFrequency(String value, int frequency) {
            this.value = value;
            this.frequency = frequency;
        }

        @Override
        public String toString() {
            return String.format("ValueWithFrequency{value='%s', frequency=%d}",
                value, frequency);
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
        int elementIndex = 0;  // Index in non-separator components list

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

            // Get suggestions using new simplified API
            IPersistentVector suggestionsVec = (IPersistentVector)
                getElementSuggestions.invoke(model, currentFilename, elementIndex);

            List<String> suggestionValues = new ArrayList<>();
            if (suggestionsVec != null) {
                for (int j = 0; j < suggestionsVec.count(); j++) {
                    suggestionValues.add((String) suggestionsVec.nth(j));
                }
            }

            // If no suggestions from model, at least include current value
            if (suggestionValues.isEmpty()) {
                suggestionValues.add(value);
            }

            components.add(new FilenameComponent(
                value, suggestionValues, componentType, elementIndex));

            // Store separator that follows this component
            if (!lastSeparator.isEmpty()) {
                separators.add(lastSeparator);
                lastSeparator = "";
            }

            elementIndex++;
        }

        return new ParsedFilename(currentFilename, extension, components, separators);
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
                frequency
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
            System.out.println("  Element " + comp.elementIndex + ":");
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

        System.out.println("\n" + repeat("=", 70) + "\n");

        // 5. Parse a single filename into components
        System.out.println("5. Parsing filename into components:");
        String filename = "Report_2024-03-Invoice_001.pdf";
        List<Component> components = parseFilenameToComponents(filename);
        System.out.println("   Filename: " + filename);
        for (Component comp : components) {
            System.out.println("   " + comp);
        }

        // 6. Get all observed values for a position (useful for combo boxes)
        System.out.println("\n6. All observed values at position 2 (numeric index) in log files:");
        List<ValueWithFrequency> allValues = getAllPositionValues(logModel, "File_003.log", 2);
        for (ValueWithFrequency val : allValues) {
            System.out.println("   - " + val.value + " (used " + val.frequency + " times)");
        }

        // 7. Get metadata about all positions
        System.out.println("\n7. Pattern structure:");
        List<PositionInfo> positions = getPatternPositions(logModel, "File_003.log");
        for (PositionInfo pos : positions) {
            System.out.println("   " + pos + ", examples: " + pos.exampleValues);
        }

        // 8. Get all available patterns
        System.out.println("\n8. Available patterns in model:");
        List<PatternInfo> patterns = getAllPatterns(imageModel);
        for (PatternInfo pattern : patterns) {
            System.out.println("   " + pattern);
            System.out.println("      Examples: " + pattern.exampleFiles);
        }
    }
}
