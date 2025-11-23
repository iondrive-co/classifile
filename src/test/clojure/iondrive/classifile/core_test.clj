(ns iondrive.classifile.core-test
  (:require [clojure.test :refer :all]
            [iondrive.classifile.core :as core]))

(deftest parsing-and-signature
  (let [f (core/parse-filename "Invoice_2024-03-Report_001.pdf")
        comps (:components f)
        sig   (-> f :signature :canonical)]
    (is (seq comps))
    (is (re-find #"WORD" sig))
    (is (re-find #"NUM" sig))
    (is (re-find #"EXT" sig))))

(deftest index-role-inference
  (let [model (core/build-model-from-names
                ["Report_001.txt"
                 "Report_002.txt"
                 "Report_003.txt"
                 "Report_005.txt"])
        group (first (:groups model))
        has-index? (some #(= :index (:role %))
                         (:position-stats group))]
    (is has-index?)))

(deftest predicts-next-sequential-index
  (let [model   (core/build-model-from-names
                  ["File_001.log"
                   "File_002.log"
                   "File_003.log"])
        result  (core/predict model 2)  ; Position 2 = last file "File_003.log"
        values  (->> (:elements result)
                     (mapcat :suggestions))]
    (is (some #{"004"} values))))
