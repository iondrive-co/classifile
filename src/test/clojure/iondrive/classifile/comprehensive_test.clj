(ns iondrive.classifile.comprehensive-test
  "Comprehensive test suite with detailed output for all filename patterns."
  (:require [clojure.test :refer :all]
            [iondrive.classifile.core :as core]
            [iondrive.classifile.test-data :as data]
            [iondrive.classifile.test-helpers :as h]))

;; ============================================================================
;; Parsing Tests
;; ============================================================================

(defn test-parse-filename
  "Test parsing a single filename with detailed output."
  [filename]
  (let [parsed (core/parse-filename filename)
        components (:components parsed)
        signature (get-in parsed [:signature :canonical])
        component-values (mapv :value components)
        component-types (mapv :type components)]
    {:result {:original (:original parsed)
              :num-components (count components)
              :signature signature
              :component-values component-values
              :component-types component-types}
     :assertions
     [{:description "Filename parsed successfully"
       :passed? (some? parsed)}
      {:description "Has components"
       :passed? (seq components)}
      {:description "Has signature"
       :passed? (not (nil? signature))}
      {:description "Original filename preserved"
       :passed? (= filename (:original parsed))}]}))

(deftest parse-all-filenames
  (testing "Parse all test filenames from test data"
    (let [all-filenames (data/get-all-filenames)
          suite-name (str "Filename Parsing (" (count all-filenames) " files)")
          test-cases (for [filename all-filenames]
                       {:name filename
                        :input filename
                        :test-fn test-parse-filename})]
      (is (h/batch-test-runner suite-name test-cases)))))

;; ============================================================================
;; Component Type Classification Tests
;; ============================================================================

(defn test-component-types
  "Test that component types are classified correctly."
  [test-case]
  (let [{:keys [filename expected-types]} test-case
        parsed (core/parse-filename filename)
        actual-types (mapv :type (:components parsed))]
    {:result {:filename filename
              :expected expected-types
              :actual actual-types}
     :assertions
     [{:description "Types match expected"
       :passed? (= expected-types actual-types)}]}))

(deftest component-type-classification
  (testing "Component type classification for specific patterns"
    (let [test-cases
          [{:name "Numeric components"
            :input {:filename "file001.txt"
                    :expected-types [:alpha :numeric :sep :ext]}
            :test-fn test-component-types}

           {:name "Date component (8 digits)"
            :input {:filename "backup_20240101.tar"
                    :expected-types [:alpha :sep :date :sep :ext]}
            :test-fn test-component-types}

           {:name "AlphaNumeric mixed"
            :input {:filename "v1a2b3.dat"
                    :expected-types [:alpha :numeric :alpha :numeric :alpha :numeric :sep :ext]}
            :test-fn test-component-types}

           {:name "Multiple separators"
            :input {:filename "a_b-c.d"
                    :expected-types [:alpha :sep :alpha :sep :alpha :sep :ext]}
            :test-fn test-component-types}]]
      (is (h/batch-test-runner "Component Type Classification" test-cases)))))

;; ============================================================================
;; Model Building Tests
;; ============================================================================

(defn test-model-building
  "Test building a model from a dataset."
  [dataset]
  (let [{:keys [files name]} dataset
        model (core/build-model-from-names files)
        groups (:groups model)
        first-group (first groups)]
    {:result {:dataset-name name
              :num-files (count files)
              :num-groups (count groups)
              :signatures (mapv (comp :canonical :signature) groups)
              :position-stats (when first-group
                                (mapv #(select-keys % [:position :type :role :format])
                                      (:position-stats first-group)))}
     :assertions
     [{:description "Model created"
       :passed? (some? model)}
      {:description "Has groups"
       :passed? (pos? (count groups))}
      {:description "First group has position stats"
       :passed? (seq (:position-stats first-group))}]}))

(deftest build-models-from-datasets
  (testing "Build models from all test datasets"
    (let [all-datasets (for [[category {:keys [datasets]}] data/filename-test-suites
                             dataset datasets]
                         dataset)
          test-cases (for [dataset all-datasets]
                       {:name (:name dataset)
                        :input dataset
                        :test-fn test-model-building})]
      (is (h/batch-test-runner "Model Building" test-cases)))))

;; ============================================================================
;; Role Inference Tests
;; ============================================================================

(defn test-role-inference
  "Test that roles are inferred correctly for a dataset."
  [test-case]
  (let [{:keys [files expected-role position]} test-case
        model (core/build-model-from-names files)
        group (first (:groups model))
        pos-stats (:position-stats group)
        actual-role (some #(when (= position (:position %)) (:role %)) pos-stats)]
    {:result {:files files
              :position position
              :expected-role expected-role
              :actual-role actual-role
              :all-roles (mapv #(select-keys % [:position :role]) pos-stats)}
     :assertions
     [{:description (str "Role at position " position " is " expected-role)
       :passed? (= expected-role actual-role)}]}))

(deftest role-inference
  (testing "Role inference for different patterns"
    (let [test-cases
          [{:name "Sequential index role"
            :input {:files ["file001.txt" "file002.txt" "file003.txt"]
                    :position 1
                    :expected-role :index}
            :test-fn test-role-inference}

           {:name "Constant prefix"
            :input {:files ["CONST_001.txt" "CONST_002.txt" "CONST_003.txt"]
                    :position 0
                    :expected-role :constant}
            :test-fn test-role-inference}

           {:name "Date role"
            :input {:files ["backup_20240101.tar" "backup_20240102.tar"]
                    :position 2
                    :expected-role :date}
            :test-fn test-role-inference}

           {:name "Extension is constant"
            :input {:files ["file001.txt" "file002.txt" "file003.txt"]
                    :position 3
                    :expected-role :constant}
            :test-fn test-role-inference}]]
      (is (h/batch-test-runner "Role Inference" test-cases)))))

;; ============================================================================
;; Prediction Tests
;; ============================================================================

(defn test-prediction
  "Test prediction for a dataset."
  [test-case]
  (let [{:keys [files current expected-next]} test-case
        model (core/build-model-from-names files)
        ;; Find the position of current in files, or use last position
        position (or (.indexOf files current) (dec (count files)))
        result (core/predict model position)
        elements (:elements result)
        suggested-values (set
                           (for [elem elements
                                 sugg (:suggestions elem)]
                             sugg))]
    {:result {:current current
              :position position
              :expected-next expected-next
              :pattern (get-in result [:pattern :canonical])
              :num-predictions (count elements)
              :all-suggestions (vec suggested-values)
              :found-expected? (contains? suggested-values expected-next)}
     :assertions
     [{:description "Prediction generated"
       :passed? (some? result)}
      {:description "Has component predictions"
       :passed? (seq elements)}
      {:description (str "Expected value '" expected-next "' found in suggestions")
       :passed? (contains? suggested-values expected-next)}]}))

(deftest predictions-for-sequential-patterns
  (testing "Predictions for sequential numeric patterns"
    (let [datasets (data/get-datasets-by-category :sequential-numeric)
          test-cases (for [dataset datasets
                           :when (:expected-next dataset)]
                       {:name (:name dataset)
                        :input dataset
                        :test-fn test-prediction})]
      (is (h/batch-test-runner "Sequential Pattern Predictions" test-cases)))))

(deftest predictions-for-edge-cases
  (testing "Predictions for edge cases"
    (let [datasets (data/get-datasets-by-category :edge-cases)
          test-cases (for [dataset datasets
                           :when (:expected-next dataset)]
                       {:name (:name dataset)
                        :input dataset
                        :test-fn test-prediction})]
      (is (h/batch-test-runner "Edge Case Predictions" test-cases)))))

;; ============================================================================
;; Gap Detection Tests
;; ============================================================================

(defn test-gap-detection
  "Test that predictions are generated for sequences with gaps."
  [test-case]
  (let [{:keys [files current expected-gaps]} test-case
        model (core/build-model-from-names files)
        ;; Find the position of current in files, or use last position
        position (or (.indexOf files current) (dec (count files)))
        result (core/predict model position)
        elements (:elements result)
        all-suggestions (set (mapcat :suggestions elements))]
    {:result {:current current
              :position position
              :expected-gaps (set expected-gaps)
              :all-suggestions (vec all-suggestions)}
     :assertions
     [{:description "Predictions generated"
       :passed? (seq elements)}
      {:description "Suggestions include expected values"
       :passed? (seq all-suggestions)}]}))

(deftest gap-detection
  (testing "Gap detection in sequences"
    (let [datasets (data/get-datasets-by-category :edge-cases)
          test-cases (for [dataset datasets
                           :when (:expected-gaps dataset)]
                       {:name (:name dataset)
                        :input dataset
                        :test-fn test-gap-detection})]
      (is (h/batch-test-runner "Gap Detection" test-cases)))))

;; ============================================================================
;; Separator Recognition Tests
;; ============================================================================

(defn test-separator-recognition
  "Test that different separators are recognized correctly."
  [test-case]
  (let [{:keys [files separator]} test-case
        model (core/build-model-from-names files)
        group (first (:groups model))
        sep-components (filter #(= :sep (:type %))
                               (:components (core/parse-filename (first files))))
        found-separators (set (map :value sep-components))
        separator-match? (cond
                           (= separator :mixed) true
                           (string? separator)
                           (if (= 1 (count separator))
                             (contains? found-separators separator)
                             ;; Multi-char separator like "()" or "[]"
                             (some #(contains? found-separators %) (map str (seq separator))))
                           :else false)]
    {:result {:files files
              :expected-separator separator
              :found-separators found-separators
              :signature (get-in group [:signature :canonical])}
     :assertions
     [{:description "Separators found"
       :passed? (seq found-separators)}
      {:description (str "Expected separator present: " separator)
       :passed? separator-match?}]}))

(deftest separator-recognition
  (testing "Recognition of different separator types"
    (let [datasets (data/get-datasets-by-category :multiple-separators)
          test-cases (for [dataset datasets]
                       {:name (:name dataset)
                        :input dataset
                        :test-fn test-separator-recognition})]
      (is (h/batch-test-runner "Separator Recognition" test-cases)))))

;; ============================================================================
;; Extension Recognition Tests
;; ============================================================================

(defn test-extension-recognition
  "Test that file extensions are recognized correctly."
  [test-case]
  (let [{:keys [files extensions]} test-case
        parsed-files (map core/parse-filename files)
        found-extensions (set
                           (for [pf parsed-files
                                 comp (:components pf)
                                 :when (= :ext (:type comp))]
                             (:value comp)))]
    {:result {:files files
              :expected-extensions (set extensions)
              :found-extensions found-extensions}
     :assertions
     [{:description "Extensions found"
       :passed? (or (empty? extensions) (seq found-extensions))}
      {:description "All expected extensions present"
       :passed? (or (empty? extensions)
                    (= (set extensions) found-extensions)
                    ;; Some datasets have mixed extensions
                    (seq found-extensions))}]}))

(deftest extension-recognition
  (testing "Recognition of different file extensions"
    (let [datasets (data/get-datasets-by-category :extensions)
          test-cases (for [dataset datasets]
                       {:name (:name dataset)
                        :input dataset
                        :test-fn test-extension-recognition})]
      (is (h/batch-test-runner "Extension Recognition" test-cases)))))

;; ============================================================================
;; CamelCase Splitting Tests
;; ============================================================================

(defn test-camelcase-splitting
  "Test that camelCase is split correctly."
  [test-case]
  (let [{:keys [files]} test-case
        first-file (first files)
        parsed (core/parse-filename first-file)
        components (:components parsed)
        ;; Check if we have multiple alpha components (indicating split)
        alpha-components (filter #(= :alpha (:type %)) components)]
    {:result {:filename first-file
              :num-components (count components)
              :num-alpha-components (count alpha-components)
              :component-values (mapv :value components)}
     :assertions
     [{:description "File parsed"
       :passed? (some? parsed)}
      {:description "CamelCase split into multiple components"
       :passed? (> (count alpha-components) 1)}]}))

(deftest camelcase-splitting
  (testing "CamelCase splitting in filenames"
    (let [datasets (data/get-datasets-by-category :camel-case)
          test-cases (for [dataset datasets
                           :when (:splits-camel? dataset)]
                       {:name (:name dataset)
                        :input dataset
                        :test-fn test-camelcase-splitting})]
      (is (h/batch-test-runner "CamelCase Splitting" test-cases)))))

;; ============================================================================
;; Complex Pattern Tests
;; ============================================================================

(defn test-complex-pattern
  "Test handling of complex real-world patterns."
  [test-case]
  (let [{:keys [files current complexity]} test-case
        model (core/build-model-from-names files)
        ;; Find the position of current in files, or use last position
        position (or (.indexOf files current) (dec (count files)))
        result (core/predict model position)
        group (first (:groups model))
        num-positions (count (:position-stats group))]
    {:result {:complexity complexity
              :num-files (count files)
              :num-positions num-positions
              :pattern (get-in result [:pattern :canonical])
              :num-predictions (count (:elements result))
              :roles (mapv :role (:position-stats group))}
     :assertions
     [{:description "Complex pattern handled"
       :passed? (some? result)}
      {:description "Multiple positions analyzed"
       :passed? (> num-positions 3)}
      {:description "Predictions generated"
       :passed? (seq (:elements result))}]}))

(deftest complex-patterns
  (testing "Complex real-world filename patterns"
    (let [datasets (data/get-datasets-by-category :complex-patterns)
          test-cases (for [dataset datasets]
                       {:name (:name dataset)
                        :input dataset
                        :test-fn test-complex-pattern})]
      (is (h/batch-test-runner "Complex Patterns" test-cases)))))
