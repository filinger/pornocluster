package com.griddynamics.bigdata.darknet.analytics.utils

import com.griddynamics.bigdata.input.xml.XMLInputFormat
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.mahout.text.wikipedia.WikipediaAnalyzer
import org.apache.spark.SparkContext
import org.apache.spark.deploy.SparkHadoopUtil
import org.apache.spark.rdd.RDD

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

/**
  * TODO
  */
object WikiPayloadExtractor {
  private val CATEGORY_PATTERN = """\[\[Category:(.{2,50}[^\|\*;:\)\(\[\]]+)\]\]""".r
  private val ARTICLE_CONTENT_PATTERN = """<text xml:space=\"preserve\">([\s\S]*?)<\/text>""".r
  private val ARTICLE_TITLE_PATTERN = """<title>(.*)<\\/title>""".r
  private val REDIRECT_PAGE = "#REDIRECT"
  private val START_DOC = "<text xml:space=\"preserve\">"
  private val END_DOC = "</text>"

  private val XML_START_TAG = "<page>"
  private val XML_END_TAG = "</page>"
  private val UNKNOWN_CATEGORY = "Unknown"

  private val wikipediaTokenizer = new WikipediaAnalyzer()

  def loadTargetCategories(sc: SparkContext, targetCategoriesPath: String): RDD[String] = {
    sc.textFile(targetCategoriesPath)

  }

  def loadWikiArticles(sc: SparkContext, wikiDumpPath: String): RDD[String] = {

    val hadoopConfiguration = SparkHadoopUtil.get.newConfiguration(sc.getConf)
    hadoopConfiguration.set(XMLInputFormat.CONF_XML_START_TAG, XML_START_TAG)
    hadoopConfiguration.set(XMLInputFormat.CONF_XML_END_TAG, XML_END_TAG)
    hadoopConfiguration.set("io.serializations",
      "org.apache.hadoop.io.serializer.JavaSerialization," +
        "org.apache.hadoop.io.serializer.WritableSerialization")

    sc.newAPIHadoopFile(
      wikiDumpPath,
      ClassTag.apply(classOf[XMLInputFormat]).runtimeClass.asInstanceOf[Class[XMLInputFormat]],
      ClassTag.apply(classOf[LongWritable]).runtimeClass.asInstanceOf[Class[LongWritable]],
      ClassTag.apply(classOf[Text]).runtimeClass.asInstanceOf[Class[Text]],
      conf = hadoopConfiguration)
      .map { case (k, v) => v.toString }
  }

  def categorizeArticlesByTarget(sc: SparkContext,
                                 articles: RDD[String],
                                 targetCategories: Map[String, Int]): RDD[(String, String)] = {
    categorizeArticles(sc, articles)
      .filter { case (k, v) =>
        val appropriateCategory = targetCategories.get(k.toLowerCase()) //TODO case ignore compare/equals?

        appropriateCategory match {
          case Some(s) => true
          case None => false
        }
      }
  }

  def categorizeArticles(sc: SparkContext, articles: RDD[String]): RDD[(String, String)] = {
    articles.map(article => (findCategory(article), getArticleContent(article)))
      .filter { case (k, v) =>
        !isUnknownCategory(k) &&
          !isRedirect(v)
      }
  }

  def isUnknownCategory(k: String): Boolean = {
    k.eq(UNKNOWN_CATEGORY)
  }

  def getArticleContent(article: String): String = {
    ARTICLE_CONTENT_PATTERN.findFirstMatchIn(article) match {
      case Some(content) => content.group(1)
      case None => ""
    }

  }

  def isRedirect(article: String): Boolean = {
    article.contains(REDIRECT_PAGE)
  }

  def tokenizeArticleContent(content: String): Seq[String] = {
    val tokens = ListBuffer.empty[String]

    //use mahout's one
    val tokenStream = wikipediaTokenizer.tokenStream("wikiArticleContent", content)
    val charTermAttribute = tokenStream.getAttribute(classOf[CharTermAttribute])

    tokenStream.reset()
    while (tokenStream.incrementToken()) {
      tokens += (charTermAttribute.toString)
    }
    tokenStream.end()
    tokenStream.close()

    tokens.toSeq
  }

  def getTitle(article: String) = article match {
    case ARTICLE_TITLE_PATTERN(title) => title
    case _ => ""
  }

  def clearCategory(article: String): String = {
    CATEGORY_PATTERN.replaceAllIn(article, "")
  }

  def findMatchingCategory(article: String, categories: Set[String]): String = {
    val c = findCategory(article)
    val cc = categories.find(s => s.eq(c))
    cc.getOrElse(UNKNOWN_CATEGORY)
  }

  def findCategory(document: String): String = {
    CATEGORY_PATTERN.findFirstMatchIn(document) match {
      case Some(name) => name.group(1)
      case None => UNKNOWN_CATEGORY
    }
  }

  def extractCategories(sc: SparkContext, documents: RDD[String]): RDD[String] = {
    val categoriesPerDoc = documents.map(doc => findCategories(doc))

    val aggregatedCategories = categoriesPerDoc.aggregate(mutable.HashSet.empty[String])(
      seqOp = (hs, v) => hs.++(v),
      combOp = (hs1, hs2) => hs1.union(hs2)
    ).toSeq

    sc.parallelize(aggregatedCategories)
      .distinct()
      .sortBy(s => s, true)

  }

  def findCategories(document: String): Seq[String] = {
    (CATEGORY_PATTERN findAllMatchIn document map {
      case CATEGORY_PATTERN(category) => category
    }
      ).toSeq
  }

}