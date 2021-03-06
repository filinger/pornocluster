package com.griddynamics.analytics.darknet.job.classification

import com.griddynamics.analytics.darknet.job.SparkJob
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.apache.spark.SparkContext
import org.apache.spark.mllib.tree.RandomForest
import org.apache.spark.mllib.util.MLUtils


/**
  * The {@link SparkJob} job implementation builds Random Forest classifier.
  */
object BuildRandomForestClassifier extends SparkJob with LazyLogging {

  /**
    * Executes the job
    * @param sc predefined Spark context
    * @param args required job arguments:
    *             #1: path to labeled points
    *             #2: number of Trees to form a quorum
    *             #3: path to result model
    *
    * @return status of job completion: '1' / '0' - success / failure
    */
  override def execute(sc: SparkContext, args: String*): Int = {
    val lpsPath = args(0)
    val numTrees = args(1).toInt
    val outputModelDirPath = args(2)

    val lps = MLUtils.loadLabeledPoints(sc, lpsPath)
      .cache()

    /**
    Train a RandomForest model.
    Empty categoricalFeaturesInfo indicates all features are continuous.
      */
    val numClasses = lps.groupBy(lp => lp.label).count().toInt
    val categoricalFeaturesInfo = Map[Int, Int]()
    val featureSubsetStrategy = "auto" // Let the algorithm choose.
    val impurity = "gini"
    val maxDepth = 4
    val maxBins = 32

    val model = RandomForest.trainClassifier(lps, numClasses, categoricalFeaturesInfo,
      numTrees, featureSubsetStrategy, impurity, maxDepth, maxBins)

    // test model's accuracy
    val predictedExpected = lps.map { lp =>
      val predictedLabel = model.predict(lp.features)
      (predictedLabel, lp.label)

    }
    val predictedCount = predictedExpected.count()
    val hitCount = predictedExpected.filter { case (l1, l2) => l1 != l2 }.count()
    val accuracy = hitCount / (predictedCount / 100)
    logger.info(s"predicted: $predictedCount \nhit: $hitCount \naccuracy: $accuracy")

    model.save(sc, outputModelDirPath)
    1
  }
}
