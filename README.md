# Classifile

Small library that looks at a directory of filenames, learns their *shape*, and then predicts likely next values for each filename component (especially numeric indices).

## How the logic works (simple overview)

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

## Code layout

- `src/main/clojure/name_model/core.clj`
  - Parsing and tokenising filenames.
  - Building the directory model.
  - Predicting next components.
  - A tiny `-main` that runs a small demo.

- `src/test/clojure/name_model/core_test.clj`
  - clojure.test-based tests for parsing, role inference, and prediction.

- `src/test/clojure/name_model/test_runner.clj`
  - Simple test runner namespace for Gradle.

## Running

From the project root:

```bash
./gradlew run
```

This runs a small demo (builds a model from a few sample filenames and prints predictions).

To run the Clojure tests:

```bash
./gradlew cljTest
```

## Using in IntelliJ IDEA

1. Open IntelliJ.
2. Choose “Open” and select the project root directory (`name-model-clj`).
3. Let IntelliJ import it as a Gradle project.
4. You can then:
   - Run the `run` Gradle task for the demo.
   - Run the `cljTest` Gradle task for tests.
   - Or use your Clojure plugin (e.g. Cursive) to work directly with the namespaces.
