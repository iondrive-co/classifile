(ns iondrive.classifile.test-runner
  "Simple test runner namespace so Gradle can run clojure.test via cljTest."
  (:require [clojure.test :as t]
            [iondrive.classifile.core-test]))

(defn -main
  [& _args]
  (let [result (t/run-tests 'iondrive.classifile.core-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
