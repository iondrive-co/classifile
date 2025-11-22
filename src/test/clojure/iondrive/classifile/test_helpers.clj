(ns iondrive.classifile.test-helpers
  "Test helpers for detailed test output and assertions.")

(defn print-test-header
  "Print a formatted header for a test case."
  [test-name]
  (println)
  (println "┌" (apply str (repeat 70 "─")) "┐")
  (println "│" test-name)
  (println "└" (apply str (repeat 70 "─")) "┘"))

(defn print-section
  "Print a section header."
  [title]
  (println)
  (println "  " title)
  (println "  " (apply str (repeat (count title) "─"))))

(defn print-input
  "Print input data in a formatted way."
  [label data]
  (println "    ✓" label "→" (pr-str data)))

(defn print-result
  "Print result data in a formatted way."
  [label data]
  (println "    ⇒" label "→" (pr-str data)))

(defn print-assertion
  "Print assertion check."
  [description passed?]
  (println "    " (if passed? "✓" "✗") description))

(defn test-case-runner
  "Run a single test case with detailed output.
   test-fn should be a function that takes input and returns {:result ... :assertions [...]}"
  [test-name input test-fn]
  (print-test-header test-name)
  (print-input "Input" input)
  (let [{:keys [result assertions]} (test-fn input)]
    (when result
      (print-section "Result")
      (doseq [[label value] result]
        (print-result (name label) value)))
    (when assertions
      (print-section "Assertions")
      (doseq [{:keys [description passed?]} assertions]
        (print-assertion description passed?)))
    (let [all-passed? (every? :passed? assertions)]
      (println)
      (println "    " (if all-passed? "✓ PASSED" "✗ FAILED"))
      all-passed?)))

(defn batch-test-runner
  "Run multiple test cases from a test suite."
  [suite-name test-cases]
  (println)
  (println "═════════════════════════════════════════════════════════════════════════")
  (println "  TEST SUITE:" suite-name)
  (println "═════════════════════════════════════════════════════════════════════════")
  (let [results (doall
                  (for [[idx {:keys [name input test-fn]}] (map-indexed vector test-cases)]
                    (let [test-name (str (inc idx) ". " name)]
                      (test-case-runner test-name input test-fn))))]
    (println)
    (println "═════════════════════════════════════════════════════════════════════════")
    (println "  SUMMARY:" suite-name)
    (println "  Total:" (count results) "| Passed:" (count (filter identity results))
             "| Failed:" (count (filter not results)))
    (println "═════════════════════════════════════════════════════════════════════════")
    (every? identity results)))
