package de.tu_berlin.citlab.storm.builder;

import backtype.storm.tuple.Tuple;
import de.tu_berlin.citlab.storm.operators.MultipleOperators;
import de.tu_berlin.citlab.storm.operators.OperatorProcessingDescription;
import de.tu_berlin.citlab.storm.operators.join.StaticHashJoinOperator;
import de.tu_berlin.citlab.storm.operators.join.TupleProjection;
import de.tu_berlin.citlab.storm.udf.IOperator;
import de.tu_berlin.citlab.storm.window.TupleComparator;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class StreamCaseMergeSource extends StreamCaseMerge {
    protected StreamNode[] sources;
    protected StreamCaseMerge casemerge;

    public StreamCaseMergeSource(StreamBuilder builder, StreamCaseMerge casemerge, MultipleOperators multipleOperators, StreamNode... sources) {
        super(builder, multipleOperators, casemerge.getGroupByFields());
        this.sources = sources;
        this.casemerge = casemerge;
    }

    public StreamCaseMerge join(Map<Serializable, List<Tuple>> hashTable,
                                TupleProjection projection,
                                TupleComparator tupleComparator) {

        final StaticHashJoinOperator staticHashJoin =
                new StaticHashJoinOperator(
                        tupleComparator,
                        projection,
                        hashTable);

        staticHashJoin.setUDFBolt(getUDFBolt());
        multipleOperators.addOperatorProcessingDescription(new OperatorProcessingDescription(staticHashJoin, getSources() ));
        return casemerge;
    }

    public StreamCaseMerge execute(IOperator operator) {
        operator.setUDFBolt(getUDFBolt());
        multipleOperators.addOperatorProcessingDescription(new OperatorProcessingDescription(operator, getSources() ));
        return casemerge;
    }

    public String[] getSources(){
        String[] sourceList = new String[sources.length];
        for (int i = 0; i < sources.length; i++)
            sourceList[i] = sources[i].getNodeId();
        return sourceList;
    }
}
