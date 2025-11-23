(ns iondrive.classifile.core
  "Core logic for parsing filenames, building a simple model over patterns,
   and predicting likely next values for each component."
  (:require [clojure.string :as str]))

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

(defn- get-suggestions-for-element
  "Get ordered suggestions for an element (without scores or reasons).
   Returns plain vector of strings ordered by likelihood.

   For PATTERN types (sequential indices):
   - If current is max observed: [next, all observed alphabetically]
   - Otherwise: [current, other observed alphabetically, next]

   For VALUE types:
   - [current, others by frequency then alphabetically]"
  [{:keys [type role numeric-values distinct-values format]} current-value]
  (cond
    ;; PATTERN type - sequential numeric indices
    (and (= :numeric type)
         (= :index role)
         (seq numeric-values))
    (let [sorted (sort numeric-values)
          mx     (last sorted)
          next-v (format-number (inc mx) format)
          all-observed (map #(format-number % format) sorted)
          all-observed-sorted (sort all-observed)
          current-num (try (Long/parseLong current-value) (catch Exception _ nil))]
      (if (and current-num (= current-num mx))
        ;; Current is max - put next first, then all observed alphabetically
        (vec (cons next-v all-observed-sorted))
        ;; Current is not max - put current first, others alphabetically, then next
        (let [others (remove #(= % current-value) all-observed-sorted)]
          (vec (concat [current-value] others [next-v])))))

    ;; VALUE type - order by frequency, then alphabetically
    (seq distinct-values)
    (let [sorted-by-freq (sort-by (fn [[v c]] [(- c) v]) distinct-values)
          all-values (map first sorted-by-freq)]
      ;; Ensure current value is first
      (let [others (remove #(= % current-value) all-values)]
        (vec (cons current-value others))))

    ;; No data - just return current value
    :else [current-value]))

(defn predict
  "Get suggestions for filename elements based on position in the file list.

   With 2 args (model, position):
     Returns all elements with suggestions for the file at that position
     position can be an integer or numeric string
     {:pattern {...} :elements [{:element-index ... :suggestions ...} ...]}

   With 3 args (model, position, element-index):
     Returns suggestions for a specific element (plain vector of strings)

   Example:
     (predict model 0)     ; All elements for position 0
     (predict model 3)     ; All elements for position 3 (next file)
     (predict model 3 0)   ; Just element 0 suggestions for position 3"
  ([model position]
   (let [pos (cond
               (integer? position) position
               (string? position) (or (parse-long position) 0)
               :else 0)
         groups (:groups model)]
     (if (seq groups)
       (let [group (first groups)
             files (:files group)
             ;; Check if position is beyond the list
             beyond-list? (>= pos (count files))
             ;; Use the file at position, or the last file if position is beyond the list
             reference-file (if (and (< pos (count files)) (>= pos 0))
                             (nth files pos)
                             (last files))
             non-sep-stats (->> (:position-stats group)
                               (remove #(#{:sep :ext} (:type %)))
                               vec)]
         {:pattern (:signature group)
          :elements
          (vec
           (for [[element-index stats] (map-indexed vector non-sep-stats)]
             (let [;; Get current value from reference file if it exists
                   ref-components (:components reference-file)
                   ref-comp (nth ref-components (:position stats) nil)
                   current-value (:value ref-comp)
                   role (:role stats)
                   comp-type (if (#{:index :date} role) :pattern :value)
                   ;; For positions beyond the list, use normal suggestions (next first when current is max)
                   ;; For existing positions, always put current first
                   suggestions (if beyond-list?
                                (get-suggestions-for-element stats current-value)
                                ;; Existing position - ensure current is first
                                (let [all-suggs (get-suggestions-for-element stats current-value)]
                                  (if (= (first all-suggs) current-value)
                                    all-suggs
                                    ;; Reorder to put current first
                                    (vec (cons current-value (remove #{current-value} all-suggs))))))]
               {:element-index element-index
                :type comp-type
                :suggestions suggestions})))})
       {:pattern nil
        :elements []})))
  ([model position element-index]
   (let [result (predict model position)
         elements (:elements result)]
     (if-let [elem (nth elements element-index nil)]
       (:suggestions elem)
       []))))



(defn get-all-position-values
  "Get all distinct values observed at a specific position in a pattern group.
   Useful for showing all possible values in a combo box.

   Returns: vector of {:value string :frequency int}
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
          (->> (:distinct-values pos-stats)
               (map (fn [[v freq]]
                      {:value v
                       :frequency freq}))
               (sort-by :frequency >)
               vec)
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

(defn get-element-suggestions
  "Get ordered suggestions for a specific element in a filename.
   Returns a simple vector of strings ordered by likelihood (no scores or reasons).

   Element ordering:
   - For existing files in the model: current value first
   - For new files beyond the model: next predicted value first (if current is max)
   - VALUE types: current first, others by frequency then alphabetically

   Arguments:
   - model: The model built from filenames
   - current-name: The current filename to analyze
   - element-index: Which element (0-based index of non-separator, non-extension components)

   Returns: vector of suggestion strings"
  [model current-name element-index]
  (let [current (parse-filename current-name)
        groups  (:groups model)]
    (if (seq groups)
      (let [[group _score]
            (apply max-key second
                   (map (fn [g] [g (score-match current g)]) groups))
            ;; Check if this filename exists in the model
            file-exists? (some #(= current-name (:original %)) (:files group))
            ;; Filter to get only non-separator, non-extension components
            non-sep-components (filter (fn [comp]
                                        (not (#{:sep :ext} (:type comp))))
                                      (:components current))
            ;; Get the element at the requested index
            current-component (nth non-sep-components element-index nil)
            current-value (:value current-component)
            position (:index current-component)
            ;; Get stats for this position from the model
            element-stats (some #(when (= position (:position %)) %)
                               (:position-stats group))]
        (if (and element-stats current-value)
          (let [suggestions (get-suggestions-for-element element-stats current-value)]
            ;; For existing files, ensure current is first
            (if file-exists?
              (if (= (first suggestions) current-value)
                suggestions
                (vec (cons current-value (remove #{current-value} suggestions))))
              ;; For new files, use natural ordering (next first if current is max)
              suggestions))
          []))
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
  "Tiny demo: build a model from a few filenames and print predictions."
  [& _args]
  (let [names  ["1. small.jpg"
                "2. medium.jpg"
                "3. large.jpg"]
        model  (build-model-from-names names)]
    (println "Demo: Model built from:")
    (doseq [n names]
      (println " -" n))
    (println "\nPredict for position 0 (first file):")
    (println (predict model 0))
    (println "\nPredict for position 2 (last file):")
    (println (predict model 2))
    (println "\nPredict for position 3 (next file after list):")
    (println (predict model 3))
    (println "\nPredict element 0 for position 3:")
    (println (predict model 3 0))))