package com.griddynamics.bigdata.xml;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by msigida on 11/24/15.
 */
public abstract class XMLMapper extends Mapper<LongWritable, Text, Text, IntWritable> {

    private final static Logger LOG = LoggerFactory.getLogger(XMLMapper.class);

    protected final static IntWritable ONE = new IntWritable(1);

    protected XPath xPath;
    protected DocumentBuilder builder;

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        xPath = XPathFactory.newInstance().newXPath();
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        try {
            builder = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            LOG.error("Error creating document builder", e);
            throw new IOException(e);
        }
    }

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        InputStream stream = new ByteArrayInputStream(value.getBytes(), 0, value.getLength());
        try {
            Document document = builder.parse(stream);
            mapXml(document, context);
        } catch (SAXException | XPathExpressionException e) {
            LOG.error("Error parsing provided XML", e);
            throw new IOException(e);
        }
    }

    protected abstract void mapXml(Document document, Context context) throws IOException, InterruptedException, XPathExpressionException;
}
