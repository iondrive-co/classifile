(ns iondrive.classifile.core
  "Core logic for parsing filenames, building a simple model over patterns,
   and predicting likely next values for each component."
  (:require [clojure.set :as set]
            [clojure.string :as str]))

;; --- Helpers -------------------------------------------------------------

(def ^:private separator-chars
  "Characters that we treat as separators between logical components."
  " ._-()[]#,~")

(defn- separator-char?
  [ch]
  (not (neg? (.indexOf separator-chars (int ch)))))

(defn- digits?
  "True if s is non-empty and all characters are digits."
  [s]
  (and (seq s)
       (every? #(Character/isDigit ^char %) s)))

(defn- letters?
  "True if s is non-empty and all characters are letters."
  [s]
  (and (seq s)
       (every? #(Character/isLetter ^char %) s)))

(defn- split-keeping-delims
  "Split string s into a sequence of tokens, keeping separator characters
   as their own one-character tokens."
  [s]
  (loop [chars (seq s)
         current ""
         tokens []]
    (if-let [ch (first chars)]
      (if (separator-char? ch)
        (recur (next chars)
               ""
               (-> tokens
                   (cond-> (not (str/blank? current)) (conj current))
                   (conj (str ch))))
        (recur (next chars)
               (str current ch)
               tokens))
      (cond-> tokens
        (not (str/blank? current)) (conj current)))))

(defn- split-alpha-numeric-mixed
  "Split a token whenever it switches between digits and non-digits.
   Example: \"File001A\" => [\"File\" \"001\" \"A\"]."
  [token]
  (->> token
       (partition-by #(Character/isDigit ^char %))
       (map (partial apply str))))

(def ^:private camel-split-pattern
  "Lowercase-to-uppercase boundary for simple camelCase splitting."
  (re-pattern "(?<=[a-z])(?=[A-Z])"))

(defn- camel-split
  "Split a token on simple camelCase boundaries."
  [s]
  (str/split s camel-split-pattern))

(defn- classify-type
  "Classify a token into a primitive component type keyword."
  [token]
  (cond
    (digits? token)
    (if (= 8 (count token))
      :date
      :numeric)

    (letters? token)
    :alpha

    :else
    :alphanum))

(defn- type->signature-element
  "Convert a component map into a signature element string."
  [{:keys [type value]}]
  (case type
    :alpha    "WORD"
    :numeric  "NUM"
    :alphanum "ALNUM"
    :date     "DATE"
    :ext      "EXT"
    :sep      (str "SEP(" value ")")
    "OTHER"))

;; --- Parsing filenames into components -----------------------------------

(defn parse-filename
  "Parse a filename into:
   {:original   string
    :components [{:value  string
                  :type   keyword
                  :role   keyword
                  :index  int}]
    :signature  {:elements  [string ...]
                 :canonical string}}"
  [name]
  (let [idx (.lastIndexOf ^String name ".")
        ;; Split into base and optional extension.
        [base ext] (if (and (pos? idx) (< idx (dec (count name))))
                     (let [cand (subs name (inc idx))]
                       (if (re-matches #"[A-Za-z0-9]{1,5}" cand)
                         [(subs name 0 idx) cand]
                         [name nil]))
                     [name nil])
        base-tokens (split-keeping-delims base)]
    (loop [tokens base-tokens
           index  0
           comps  []]
      (if-let [tok (first tokens)]
        (if (and (= 1 (count tok))
                 (separator-char? (first tok)))
          ;; Single separator char token.
          (recur (next tokens)
                 (inc index)
                 (conj comps {:value tok
                              :type  :sep
                              :role  :constant
                              :index index}))
          ;; Non-separator: split camelCase and alpha/numeric runs.
          (let [parts (mapcat split-alpha-numeric-mixed (camel-split tok))
                [comps index]
                (reduce
                  (fn [[cs idx] sub]
                    (let [t    (classify-type sub)
                          role (if (= t :date) :date :unknown)]
                      [(conj cs {:value sub
                                 :type  t
                                 :role  role
                                 :index idx})
                       (inc idx)]))
                  [comps index]
                  parts)]
            (recur (next tokens) index comps)))
        ;; Done with base; handle extension if present.
        (let [comps (if ext
                      (-> comps
                          (conj {:value "." :type :sep :role :constant :index index})
                          (conj {:value ext :type :ext :role :constant :index (inc index)}))
                      comps)
              elements  (map type->signature-element comps)
              signature {:elements  (vec elements)
                         :canonical (str/join "|" elements)}]
          {:original   name
           :components (vec comps)
           :signature  signature})))))

;; --- Model building over a set of filenames ------------------------------

(defn- most-common
  "Return the most common element in xs."
  [xs]
  (->> xs
       frequencies
       (apply max-key val)
       key))

(defn- infer-role
  "Infer a higher-level role for a position given type, distinct value counts,
   and numeric values (if any)."
  [type distinct-values numeric-values]
  (cond
    (#{:ext :sep} type) :constant
    (= :date type)      :date
    (= 1 (count distinct-values)) :constant

    (and (= :numeric type)
         (>= (count numeric-values) 2))
    (let [sorted (sort numeric-values)
          mn     (first sorted)
          mx     (last sorted)
          range  (inc (- mx mn))]
      (if (pos? range)
        (let [density (/ (double (count sorted))
                         (double range))]
          (if (> density 0.5)
            :index
            :unknown))
        :unknown))

    :else :unknown))

(defn- infer-format
  "Infer a simple formatting pattern for a position (zero-padded numeric,
   or date pattern)."
  [type components]
  (cond
    ;; numeric width: e.g. 001 -> %03d
    (and (= :numeric type) (seq components))
    (let [len (count (:value (first components)))]
      (when (and (> len 1)
                 (every? #(= len (count (:value %))) components))
        (format "%%0%dd" len)))

    ;; date pattern
    (and (= :date type) (seq components))
    (let [v (:value (first components))]
      (cond
        (re-matches #"\d{8}" v)         "yyyyMMdd"
        (re-matches #"\d{4}-\d{2}-\d{2}" v) "yyyy-MM-dd"
        :else nil))

    :else nil))

(defn- build-position-stats
  "Compute statistics for a single position across a group of filenames."
  [pos components]
  (let [type           (most-common (map :type components))
        distinct       (frequencies (map :value components))
        numeric-values (if (= :numeric type)
                         (keep #(parse-long (:value %)) components)
                         [])
        role           (infer-role type distinct numeric-values)
        fmt            (infer-format type components)]
    {:position        pos
     :type            type
     :distinct-values distinct
     :numeric-values  (vec numeric-values)
     :role            role
     :format          fmt}))

(defn- build-group
  "Given a collection of parsed filenames that share a signature, build a
   group with per-position statistics."
  [files]
  (let [sig        (:signature (first files))
        max-len    (apply max (map #(count (:components %)) files))
        pos-stats  (->> (range max-len)
                        (map (fn [pos]
                               (let [components (keep #(nth (:components %) pos nil) files)]
                                 (when (seq components)
                                   (build-position-stats pos components)))))
                        (remove nil?)
                        vec)]
    {:signature       sig
     :files           (vec files)
     :position-stats  pos-stats}))

(defn build-model-from-names
  "Build a directory-style model from a collection of filename strings.

   Returns:
   {:groups
     [{:signature      {:elements [...] :canonical \"...\"}
       :files          [parsed-file ...]
       :position-stats [{:position ...
                         :type ...
                         :role ...
                         ...} ...]} ...]}"
  [names]
  (let [files  (map parse-filename names)
        groups (->> files
                    (group-by (comp :canonical :signature))
                    (map (fn [[_ fs]] (build-group fs)))
                    (sort-by (comp :canonical :signature)))]
    {:groups (vec groups)}))

(defn build-model-from-directory
  "Read all regular files in a directory and build a model over their names."
  [dir]
  (let [path   (java.nio.file.Paths/get dir (make-array String 0))
        stream (java.nio.file.Files/newDirectoryStream path)]
    (with-open [s stream]
      (build-model-from-names
        (for [p s
              :when (java.nio.file.Files/isRegularFile p (make-array java.nio.file.LinkOption 0))]
          (str (.getFileName p)))))))

;; --- Prediction ----------------------------------------------------------

(defn- format-number
  "Format a numeric value using a simple %0Nd pattern if provided."
  [n fmt]
  (if fmt
    (if-let [[_ width] (re-matches #"%0(\d+)d" fmt)]
      (format (str "%0" width "d") (long n))
      (str n))
    (str n)))

(defn- score-match
  "Score how well a parsed current filename matches a group.
   Higher is better; negative means 'probably not this pattern'."
  [current group]
  (let [curr-comps (:components current)
        stats      (:position-stats group)
        max-len    (max (count curr-comps) (count stats))]
    (loop [i 0
           score 0]
      (if (< i max-len)
        (let [cur (nth curr-comps i nil)
              cs  (nth stats i nil)]
          (if (or (nil? cur) (nil? cs))
            (recur (inc i) (- score 2))
            (let [score (cond-> score
                          (= (:type cur) (:type cs)) (+ 2)
                          (and (= :sep (:type cur))
                               (= :sep (:type cs))
                               (= (:value cur)
                                  (-> cs :distinct-values keys first)))
                          (+ 2)
                          (and (= :constant (:role cs))
                               (contains? (:distinct-values cs) (:value cur)))
                          (+ 1))]
              (recur (inc i) score))))
        score))))

(defn- predict-for-position
  "Given position stats, produce a sequence of suggestions:
   {:value string :score double :reason string}."
  [{:keys [type role numeric-values distinct-values format]}]
  (cond
    (and (= :numeric type)
         (= :index role)
         (seq numeric-values))
    (let [sorted (sort numeric-values)
          mx     (last sorted)
          next-v (format-number (inc mx) format)
          mn     (first sorted)
          full   (set (range mn (inc mx)))
          gaps   (sort (set/difference full (set sorted)))]
      (into [{:value  next-v
              :score  0.9
              :reason "next sequential index"}]
            (map-indexed
              (fn [i g]
                {:value  (format-number g format)
                 :score  (max 0.3 (- 0.7 (* 0.05 i)))
                 :reason "fill missing index"}))
            gaps))

    (seq distinct-values)
    (let [total (double (reduce + (vals distinct-values)))]
      (->> distinct-values
           (sort-by val >)
           (take 5)
           (map (fn [[v c]]
                  {:value  v
                   :score  (/ (double c) total)
                   :reason (if (= :constant role)
                             "constant"
                             "frequent value")}))))

    :else []))

(defn predict
  "Given a model and a current filename, return a prediction map:

   {:pattern              {:elements [...] :canonical \"...\"}
    :component-predictions
      [{:position   int
        :suggestions [{:value string :score double :reason string} ...]} ...]}"
  [model current-name]
  (let [current (parse-filename current-name)
        groups  (:groups model)]
    (if (seq groups)
      (let [[group score]
            (apply max-key second
                   (map (fn [g] [g (score-match current g)]) groups))]
        (if (neg? score)
          {:pattern (:signature current)
           :component-predictions []}
          {:pattern (:signature group)
           :component-predictions
           (->> (:position-stats group)
                (map (fn [cs]
                       {:position   (:position cs)
                        :suggestions (vec (predict-for-position cs))}))
                vec)}))
      {:pattern (:signature current)
       :component-predictions []})))


(defn get-position-suggestions
  "Get suggestions for a specific position in the best-matching pattern group.

   Options:
   - :limit N - Return at most N suggestions (default: 10)
   - :min-score X - Only return suggestions with score >= X (default: 0.0)

   Returns: vector of {:value string :score double :reason string}"
  ([model current-name position]
   (get-position-suggestions model current-name position {}))
  ([model current-name position {:keys [limit min-score]
                                 :or {limit 10 min-score 0.0}}]
   (let [result (predict model current-name)
         pos-pred (some #(when (= position (:position %)) %)
                        (:component-predictions result))]
     (if pos-pred
       (->> (:suggestions pos-pred)
            (filter #(>= (:score %) min-score))
            (take limit)
            vec)
       []))))

(defn get-all-position-values
  "Get all distinct values observed at a specific position in a pattern group.
   Useful for showing all possible values in a combo box.

   Returns: vector of {:value string :frequency int :score double}
   Sorted by frequency (most common first)."
  [model current-name position]
  (let [current (parse-filename current-name)
        groups  (:groups model)]
    (if (seq groups)
      (let [[group _score]
            (apply max-key second
                   (map (fn [g] [g (score-match current g)]) groups))
            pos-stats (some #(when (= position (:position %)) %)
                            (:position-stats group))]
        (if pos-stats
          (let [distinct-vals (:distinct-values pos-stats)
                total (double (reduce + (vals distinct-vals)))]
            (->> distinct-vals
                 (map (fn [[v freq]]
                        {:value v
                         :frequency freq
                         :score (/ (double freq) total)}))
                 (sort-by :frequency >)
                 vec))
          []))
      [])))

(defn get-pattern-positions
  "Get information about all positions in the best-matching pattern group.
   Returns vector of position metadata for building dynamic UIs.

   Each position includes:
   - :position - Position index
   - :type - Component type (:alpha, :numeric, :date, etc.)
   - :role - Inferred role (:index, :constant, :date, :unknown)
   - :format - Format string if applicable (e.g., %03d)
   - :value-count - Number of distinct values observed
   - :example-values - Up to 3 example values"
  [model current-name]
  (let [current (parse-filename current-name)
        groups  (:groups model)]
    (if (seq groups)
      (let [[group _score]
            (apply max-key second
                   (map (fn [g] [g (score-match current g)]) groups))]
        (->> (:position-stats group)
             (map (fn [pos-stat]
                    (let [distinct-vals (:distinct-values pos-stat)
                          examples (take 3 (keys distinct-vals))]
                      {:position (:position pos-stat)
                       :type (:type pos-stat)
                       :role (:role pos-stat)
                       :format (:format pos-stat)
                       :value-count (count distinct-vals)
                       :example-values (vec examples)})))
             vec))
      [])))

(defn get-all-patterns
  "Get information about all pattern groups in the model.
   Useful for letting users choose which pattern to use.

   Returns vector of:
   - :signature - Pattern signature string
   - :file-count - Number of files matching this pattern
   - :example-files - Up to 3 example filenames"
  [model]
  (->> (:groups model)
       (map (fn [group]
              (let [files (:files group)
                    examples (take 3 (map :original files))]
                {:signature (get-in group [:signature :canonical])
                 :file-count (count files)
                 :example-files (vec examples)})))
       vec))

(defn -main
  "Tiny demo: build a model from a few filenames and print predictions
   for the last one."
  [& _args]
  (let [names  ["File_001.log"
                "File_002.log"
                "File_003.log"]
        model  (build-model-from-names names)
        result (predict model "File_003.log")]
    (println "Demo prediction for \"File_003.log\" based on:")
    (doseq [n names]
      (println " -" n))
    (println "\nResult:")
    (prn result)))
