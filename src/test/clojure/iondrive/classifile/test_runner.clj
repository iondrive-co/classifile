(ns iondrive.classifile.test-runner
  "Enhanced test runner with detailed output for all test suites."
  (:require [clojure.test :as t]
            [iondrive.classifile.comprehensive-test]
            [iondrive.classifile.core-test]))

(defn print-test-banner
  [title]
  (println)
  (println "╔═══════════════════════════════════════════════════════════════════════╗")
  (println "║" title)
  (println "╚═══════════════════════════════════════════════════════════════════════╝"))

(defn print-summary
  [results]
  (let [total-tests (reduce + (map :test results))
        total-pass (reduce + (map :pass results))
        total-fail (reduce + (map :fail results))
        total-error (reduce + (map :error results))
        total-assertions (reduce + (map (fn [r] (+ (:pass r) (:fail r) (:error r))) results))]
    (println)
    (println "╔═══════════════════════════════════════════════════════════════════════╗")
    (println "║ FINAL SUMMARY")
    (println "╠═══════════════════════════════════════════════════════════════════════╣")
    (println "║ Total Tests:     " total-tests)
    (println "║ Total Assertions:" total-assertions)
    (println "║ Passed:          " total-pass)
    (println "║ Failed:          " total-fail)
    (println "║ Errors:          " total-error)
    (println "╚═══════════════════════════════════════════════════════════════════════╝")
    (println)
    (if (pos? (+ total-fail total-error))
      (println "❌ TESTS FAILED")
      (println "✅ ALL TESTS PASSED"))
    (println)))

(defn run-namespace-tests
  [ns-sym ns-name]
  (print-test-banner ns-name)
  (let [result (t/run-tests ns-sym)]
    (println)
    (println "Results for" ns-name ":")
    (println "  Tests:     " (:test result))
    (println "  Assertions:" (+ (:pass result) (:fail result) (:error result)))
    (println "  Passed:    " (:pass result))
    (println "  Failed:    " (:fail result))
    (println "  Errors:    " (:error result))
    result))

(defn -main
  [& _args]
  (println)
  (println "╔═══════════════════════════════════════════════════════════════════════╗")
  (println "║ CLASSIFILE TEST SUITE")
  (println "║ Running all tests with detailed output...")
  (println "╚═══════════════════════════════════════════════════════════════════════╝")

  (let [results [(run-namespace-tests 'iondrive.classifile.core-test
                                      "Core Functionality Tests")
                 (run-namespace-tests 'iondrive.classifile.comprehensive-test
                                      "Comprehensive Test Suite")]
        total-failures (reduce + (map #(+ (:fail %) (:error %)) results))]

    (print-summary results)

    (when (pos? total-failures)
      (System/exit 1))))
