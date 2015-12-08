package com.griddynamics.bigdata.html;


import com.griddynamics.bigdata.CountingJob;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.util.ToolRunner;

/**
 * Created by msigida on 11/24/15.
 */
public class HtmlCounter extends CountingJob {

    @Override
    public Class<? extends Mapper> getMapperClass() {
        return HtmlMapper.class;
    }

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new HtmlCounter(), args);
    }
}