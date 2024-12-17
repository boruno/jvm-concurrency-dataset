# concurrency-tools/lincheck-incorrect-data-structures-dataset

This is a repository
for storing dataset with incorrect concurrent code for [Lincheck](https://github.com/JetBrains/lincheck) tool.

List of potential data sources can be found in [Lincheck Benchmark outline](https://docs.google.com/document/d/1HXOILLMJ1dVA6algJ-YIau4ce4f6aHlxqIAwSsSztj8/edit#heading=h.oa025wo0dk8p).

Currently, the dataset contains:
 - Student submissions. Initial source – [Amazon S3 bucket](https://us-east-2.console.aws.amazon.com/s3/buckets/mpp2022incorrectimplementations?region=us-east-2&bucketType=general&tab=objects). More than a half of those submissions were duplicated and/or contained unfinished functions and were removed from the dataset.
 - Clustered student submissions. Using HDBSCAN and Edit Distance Clustering algorithms, two reduced datasets were produced. Corresponding scripts for re-creation could be found in [HDBSCAN](data/clusteredStudentSolutions/HDBSCAN) and [EditDistanceClustering](data/clusteredStudentSolutions/editDistanceClustering) folders.
 - Concurrency libraries dataset. Data structures from java.util.concurrent, google.guava, agrona, JCTools can be found in the corresponding folder. Also, tests presented in Lincheck paper from CAV-2023 were added, featuring data structures from other papers. These tests could be executed using RunAllTests.kt file, where you need to list all directory names you want to test.
