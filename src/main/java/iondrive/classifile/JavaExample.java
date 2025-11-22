package iondrive.classifile;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;

import java.util.ArrayList;
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
     * Example usage demonstrating the complete workflow.
     */
    public static void main(String[] args) {
        System.out.println("=== Classifile Java Interop Example ===\n");

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
    }
}
