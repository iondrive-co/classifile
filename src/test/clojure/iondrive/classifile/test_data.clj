(ns iondrive.classifile.test-data
  "Comprehensive test data covering diverse filename structures.")

(def filename-test-suites
  "Collection of filename test datasets organized by pattern type."

  {:sequential-numeric
   {:description "Files with sequential numeric indices"
    :datasets
    [{:name "Simple sequential with underscores"
      :files ["Report_001.txt" "Report_002.txt" "Report_003.txt" "Report_005.txt"]
      :current "Report_005.txt"
      :expected-next "006"}

     {:name "Sequential with dashes"
      :files ["log-0001.log" "log-0002.log" "log-0003.log"]
      :current "log-0003.log"
      :expected-next "0004"}

     {:name "No padding sequential"
      :files ["file1.dat" "file2.dat" "file3.dat"]
      :current "file3.dat"
      :expected-next "4"}

     {:name "Mixed padding (should still work)"
      :files ["item001.bin" "item002.bin" "item003.bin"]
      :current "item003.bin"
      :expected-next "004"}

     {:name "Large numbers"
      :files ["chunk_9998.data" "chunk_9999.data" "chunk_10000.data"]
      :current "chunk_10000.data"
      :expected-next "10001"}]}

   :date-patterns
   {:description "Files with date components"
    :datasets
    [{:name "ISO date YYYYMMDD"
      :files ["backup_20240101.tar" "backup_20240102.tar" "backup_20240103.tar"]
      :current "backup_20240103.tar"
      :contains-date? true}

     {:name "ISO date with dashes"
      :files ["log-2024-01-01.txt" "log-2024-01-02.txt" "log-2024-01-03.txt"]
      :current "log-2024-01-03.txt"
      :contains-date? true}

     {:name "Mixed date and index"
      :files ["Report_20240301_001.pdf" "Report_20240301_002.pdf" "Report_20240302_001.pdf"]
      :current "Report_20240302_001.pdf"
      :contains-date? true}]}

   :camel-case
   {:description "Files with camelCase components"
    :datasets
    [{:name "Simple camelCase"
      :files ["ProjectAlpha001.doc" "ProjectAlpha002.doc" "ProjectBeta001.doc"]
      :current "ProjectAlpha002.doc"
      :splits-camel? true}

     {:name "Multiple camelCase words"
      :files ["myProjectFileData_v1.xml" "myProjectFileData_v2.xml"]
      :current "myProjectFileData_v2.xml"
      :splits-camel? true}

     {:name "CamelCase with numbers"
      :files ["FileV1A.txt" "FileV1B.txt" "FileV2A.txt"]
      :current "FileV2A.txt"
      :splits-camel? true}]}

   :multiple-separators
   {:description "Files using various separator types"
    :datasets
    [{:name "Underscores only"
      :files ["my_file_001.txt" "my_file_002.txt"]
      :current "my_file_002.txt"
      :separator "_"}

     {:name "Dashes only"
      :files ["my-file-001.txt" "my-file-002.txt"]
      :current "my-file-002.txt"
      :separator "-"}

     {:name "Spaces"
      :files ["my file 001.txt" "my file 002.txt"]
      :current "my file 002.txt"
      :separator " "}

     {:name "Mixed separators"
      :files ["Project_Alpha-v1.0.doc" "Project_Alpha-v1.1.doc" "Project_Beta-v1.0.doc"]
      :current "Project_Beta-v1.0.doc"
      :separator :mixed}

     {:name "Dots as separators"
      :files ["file.001.backup" "file.002.backup" "file.003.backup"]
      :current "file.003.backup"
      :separator "."}

     {:name "Parentheses"
      :files ["Document(1).pdf" "Document(2).pdf" "Document(3).pdf"]
      :current "Document(3).pdf"
      :separator "()"}

     {:name "Brackets"
      :files ["Image[001].png" "Image[002].png" "Image[003].png"]
      :current "Image[003].png"
      :separator "[]"}

     {:name "Hash marks"
      :files ["Photo#001.jpg" "Photo#002.jpg" "Photo#003.jpg"]
      :current "Photo#003.jpg"
      :separator "#"}]}

   :extensions
   {:description "Various file extensions"
    :datasets
    [{:name "Common document extensions"
      :files ["file001.pdf" "file002.docx" "file003.xlsx" "file004.txt"]
      :extensions ["pdf" "docx" "xlsx" "txt"]}

     {:name "Programming extensions"
      :files ["module001.clj" "module002.java" "module003.py" "module004.js"]
      :extensions ["clj" "java" "py" "js"]}

     {:name "Archive extensions"
      :files ["backup001.zip" "backup002.tar" "backup003.gz" "backup004.7z"]
      :extensions ["zip" "tar" "gz" "7z"]}

     {:name "Media extensions"
      :files ["video001.mp4" "video002.avi" "video003.mkv" "audio001.mp3"]
      :extensions ["mp4" "avi" "mkv" "mp3"]}

     {:name "No extension"
      :files ["file001" "file002" "file003"]
      :extensions []}]}

   :complex-patterns
   {:description "Complex real-world filename patterns"
    :datasets
    [{:name "Invoice pattern"
      :files ["Invoice_2024-03-Report_001.pdf"
              "Invoice_2024-03-Report_002.pdf"
              "Invoice_2024-04-Report_001.pdf"]
      :current "Invoice_2024-04-Report_001.pdf"
      :complexity :high}

     {:name "Log file pattern with timestamp"
      :files ["app_20240301_error_001.log"
              "app_20240301_error_002.log"
              "app_20240301_warn_001.log"]
      :current "app_20240301_error_002.log"
      :complexity :high}

     {:name "Versioned documents"
      :files ["Requirements_v1.0_Draft.docx"
              "Requirements_v1.1_Draft.docx"
              "Requirements_v1.1_Final.docx"]
      :current "Requirements_v1.1_Final.docx"
      :complexity :high}

     {:name "Multi-component pattern"
      :files ["CompanyName_ProjectAlpha_Module001_2024Q1_v1.2.3.jar"
              "CompanyName_ProjectAlpha_Module001_2024Q1_v1.2.4.jar"
              "CompanyName_ProjectAlpha_Module002_2024Q1_v1.0.0.jar"]
      :current "CompanyName_ProjectAlpha_Module002_2024Q1_v1.0.0.jar"
      :complexity :very-high}]}

   :edge-cases
   {:description "Edge cases and unusual patterns"
    :datasets
    [{:name "Single file (no pattern)"
      :files ["lonely_file.txt"]
      :current "lonely_file.txt"
      :edge-case :single-file}

     {:name "All identical except index"
      :files ["x001x" "x002x" "x003x"]
      :current "x003x"
      :expected-next "004"}

     {:name "Very long filename"
      :files [(str "This_Is_A_Very_Long_Filename_With_Many_Components_"
                   "And_It_Just_Keeps_Going_001.txt")
              (str "This_Is_A_Very_Long_Filename_With_Many_Components_"
                   "And_It_Just_Keeps_Going_002.txt")]
      :current (str "This_Is_A_Very_Long_Filename_With_Many_Components_"
                    "And_It_Just_Keeps_Going_002.txt")
      :edge-case :long-name}

     {:name "Numbers everywhere"
      :files ["123_456_789_001.dat" "123_456_789_002.dat"]
      :current "123_456_789_002.dat"
      :edge-case :many-numbers}

     {:name "Gaps in sequence"
      :files ["file001.txt" "file002.txt" "file003.txt" "file005.txt" "file006.txt" "file008.txt"]
      :current "file008.txt"
      :edge-case :gaps
      :expected-gaps [4 7]}

     {:name "Different patterns mixed"
      :files ["alpha001.txt" "beta-002.log" "gamma_003.dat"]
      :current "gamma_003.dat"
      :edge-case :mixed-patterns}

     {:name "Empty or minimal components"
      :files ["_.txt" "__.txt" "___.txt"]
      :current "___.txt"
      :edge-case :minimal}

     {:name "Unicode characters (if supported)"
      :files ["文件001.txt" "文件002.txt" "文件003.txt"]
      :current "文件003.txt"
      :edge-case :unicode}]}

   :constants-and-variables
   {:description "Mix of constant and variable components"
    :datasets
    [{:name "Constant prefix variable index"
      :files ["CONST_001.txt" "CONST_002.txt" "CONST_003.txt"]
      :current "CONST_003.txt"
      :constant-prefix "CONST"}

     {:name "Variable prefix constant suffix"
      :files ["Alpha_FIXED.txt" "Beta_FIXED.txt" "Gamma_FIXED.txt"]
      :current "Gamma_FIXED.txt"
      :constant-suffix "FIXED"}

     {:name "Multiple constants"
      :files ["PROJ_MODULE_001_v1.txt" "PROJ_MODULE_002_v1.txt"]
      :current "PROJ_MODULE_002_v1.txt"
      :constants ["PROJ" "MODULE" "v1"]}]}})

(def all-test-filenames
  "Flattened list of all test filenames for quick iteration."
  (vec
    (for [[category {:keys [datasets]}] filename-test-suites
          dataset datasets
          filename (:files dataset)]
      {:category category
       :dataset-name (:name dataset)
       :filename filename
       :dataset dataset})))

(defn get-datasets-by-category
  "Get all datasets for a specific category."
  [category]
  (get-in filename-test-suites [category :datasets]))

(defn get-all-filenames
  "Get all filenames from all datasets."
  []
  (mapv :filename all-test-filenames))
