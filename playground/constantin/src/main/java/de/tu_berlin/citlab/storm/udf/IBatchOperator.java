package de.tu_berlin.citlab.storm.udf;

import java.util.List;

import backtype.storm.tuple.Values;

public interface IBatchOperator<K> extends IBatchExecutable<K, Values, List<Values[]>>{

}
