# jvm-concurrency-dataset

This is a repository
for storing dataset with incorrect concurrent code for [Lincheck](https://github.com/JetBrains/lincheck) tool.

List of potential data sources can be found in [Lincheck Benchmark outline](https://docs.google.com/document/d/1HXOILLMJ1dVA6algJ-YIau4ce4f6aHlxqIAwSsSztj8/edit#heading=h.oa025wo0dk8p).

Currently, the dataset contains:
 - Student submissions. Initial source – [Amazon S3 bucket](https://us-east-2.console.aws.amazon.com/s3/buckets/mpp2022incorrectimplementations?region=us-east-2&bucketType=general&tab=objects). More than a half of those submissions were duplicated and/or contained unfinished functions and were removed from the dataset.
 - Clustered student submissions. Using HDBSCAN and Edit Distance Clustering algorithms, two reduced datasets were produced. Corresponding scripts for re-creation could be found in [HDBSCAN](data/clusteredStudentSolutions/HDBSCAN) and [EditDistanceClustering](data/clusteredStudentSolutions/editDistanceClustering) folders.
 - Concurrency libraries dataset. Data structures from [java.util.concurrent](data/concurrencyLibsDataset/src/test/kotlin/org/jetbrains/research/juc), [google.guava](data/concurrencyLibsDataset/src/test/kotlin/org/jetbrains/research/guava), [agrona](data/concurrencyLibsDataset/src/test/kotlin/org/jetbrains/research/agrona), [JCTools](data/concurrencyLibsDataset/src/test/kotlin/org/jetbrains/research/jctools) can be found in the corresponding folder. Also, [tests](data/concurrencyLibsDataset/src/test/kotlin/org/jetbrains/research/cav23) presented in [Lincheck paper from CAV-2023](https://nikitakoval.org/publications/cav23-lincheck.pdf) were added, featuring data structures from other papers. These tests could be executed using [RunAllTests.kt](data/concurrencyLibsDataset/src/test/kotlin/org/jetbrains/research/RunAllTests.kt) file, where you need to list all directory names you want to test.

Student submissions reduction history:
 - Using [DuplicateDeleter.py](scripts/DuplicateDeleter.py) script, full duplicates were found and deleted
 - After that, using [TODODeleter.py](scripts/TODODeleter.py), all submissions with unimplemented functions were eliminated
 - With [ProGuard](https://www.guardsquare.com/proguard) tool, [DuplicateByteCodeFinder](scripts/DuplicateByteCodeFinder.py) script eliminated two more files, that were shown as full bytecode duplicates
 - Many solutions had `package` declarations in them, which were commented with the corresponding [script](scripts/PackageCommentingScript.py). Also, `import` statements featuring unrelated paths were [shortened](scripts/ImportShorteningScript.py).
 - Using [HDBSCAN](data/clusteredStudentSolutions/HDBSCAN/HDBSCANModel.py) and [Edit distance clustering](data/clusteredStudentSolutions/editDistanceClustering/EDCModel.py) models and corresponding scripts ([HDBSCAN](data/clusteredStudentSolutions/HDBSCAN/HDBSCANReductionScript.py), [EDC](data/clusteredStudentSolutions/editDistanceClustering/EDCReductionScript.py)), two reduced datasets were created, shrinking ~25k files from the previous step to ~250 files in each.

To run tests on clustered student solutions, run this:
```bash
python3 scripts/StudentSolutionTest.py
```