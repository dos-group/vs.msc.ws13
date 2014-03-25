package de.tu_berlin.citlab.storm.topologies;

import backtype.storm.generated.StormTopology;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import de.tu_berlin.citlab.db.CassandraConfig;
import de.tu_berlin.citlab.db.PrimaryKey;
import de.tu_berlin.citlab.storm.builder.StreamBuilder;
import de.tu_berlin.citlab.storm.builder.StreamNode;
import de.tu_berlin.citlab.storm.builder.StreamSource;
import de.tu_berlin.citlab.storm.builder.TwitterStreamSource;
import de.tu_berlin.citlab.storm.helpers.KeyConfigFactory;
import de.tu_berlin.citlab.storm.helpers.TupleHelper;
import de.tu_berlin.citlab.storm.operators.Filter;
import de.tu_berlin.citlab.storm.operators.FlatMapper;
import de.tu_berlin.citlab.storm.operators.Mapper;
import de.tu_berlin.citlab.storm.operators.Reducer;
import de.tu_berlin.citlab.storm.operators.join.TupleProjection;
import de.tu_berlin.citlab.storm.sinks.CassandraSink;
import de.tu_berlin.citlab.storm.window.TimeWindow;
import de.tu_berlin.citlab.storm.window.Window;

import java.util.ArrayList;
import java.util.List;


public class AnalyzeTweetsTopologyWithStreamBuilder implements TopologyCreation {

    @Override
    public StormTopology createTopology() {
        try{
            String cassandraServerIP = "127.0.0.1";

            CassandraConfig cassandraTweetsCfg = new CassandraConfig();
            CassandraConfig cassandraUserSignificanceCfg = new CassandraConfig();
            CassandraConfig cassandraBadWordsStatisticsCfg = new CassandraConfig();

            cassandraTweetsCfg.setIP(cassandraServerIP);
            cassandraUserSignificanceCfg.setIP(cassandraServerIP);
            cassandraBadWordsStatisticsCfg.setIP(cassandraServerIP);

            cassandraTweetsCfg.setParams(  //optional, but defaults not always sensable
                    "citstorm",
                    "tweets",
                    new PrimaryKey("user", "tweet_id"), /* CassandraFactory.PrimaryKey(..)  */
                    new Fields(), /*save all fields ->  CassandraFactory.SAVE_ALL_FIELD  */
                    false // no counter
            );

            cassandraUserSignificanceCfg.setParams(  //optional, but defaults not always sensable
                    "citstorm",
                    "user_significance",
                    new PrimaryKey("user"), /* CassandraFactory.PrimaryKey(..)  */
                    new Fields("significance"), /*save all fields ->  CassandraFactory.SAVE_ALL_FIELD  */
                    true // enable counter-mode
            );

            cassandraBadWordsStatisticsCfg.setParams(  //optional, but defaults not always sensable
                    "citstorm",
                    "badword_occurences",
                    new PrimaryKey("word"), /* CassandraFactory.PrimaryKey(..)  */
                    new Fields( "count" ), /*save all fields ->  CassandraFactory.SAVE_ALL_FIELD  */
                    true // enable counter-mode
            );


            Window<Tuple, List<Tuple>> STREAM_WINDOW = new TimeWindow<Tuple>(1,1);


            List<Tuple> badWordJoinSide = new ArrayList<Tuple>();

            badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("google", new Long(1))) );
            badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("microsoft", new Long(1) )) );
            badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("facebook", new Long(1) )) );


            String[] keywords = new String[] {"der", "die","das","wir","ihr","sie", "dein", "mein", "facebook", "google", "twitter" };
            String[] languages = new String[] {"de"};
            String[] outputfields = new String[] {"user", "tweet_id", "tweet"};

            StreamBuilder stream = new StreamBuilder();
            stream.setDefaultWindowType(new TimeWindow<Tuple>(1,1));

            StreamSource tweets = new TwitterStreamSource(stream).subscribe(keywords, languages, outputfields);

            CassandraSink tweetsSink = new CassandraSink(cassandraTweetsCfg);
            CassandraSink userSignificanceSink = new CassandraSink(cassandraUserSignificanceCfg);
            CassandraSink badWordsStatisticsSink = new CassandraSink(cassandraBadWordsStatisticsCfg);

            StreamNode badWords =
                tweets.flapMap(new FlatMapper() {
                        @Override
                        public List<List<Object>> flatMap(Tuple tuple) {
                            String[] words = tuple.getStringByField("tweet")
                                    .replaceAll("[^a-zA-Z0-9 ]", "").split(" ");
                            List<List<Object>> result = new ArrayList<>();
                            for (String word : words) {
                                result.add(new Values(tuple.getValueByField("user"),
                                        tuple.getValueByField("tweet_id"),
                                        word.trim().toLowerCase()));
                            }
                            return result;
                        }
                    },
                    new Fields("user", "tweet_id", "word"));

            // prepare data to save in cassandra
            badWords.map( new Mapper() {
                            @Override
                            public List<Object> map(Tuple tuple) {
                                return new Values(tuple.getValueByField("word"), new Long(1) );
                            }
                        },
                        new Fields( "word", "count" ))
                    .save( badWordsStatisticsSink );


            badWords.join( badWordJoinSide.iterator(),
                        TupleProjection.project(new Fields("user", "tweet_id", "word"), new Fields("significance")),
                        KeyConfigFactory.compareByFields(new Fields("word")),
                        new Fields("user", "tweet_id", "word", "significance"))
                   .reduce(new Fields("user", "tweet_id"),
                           new Reducer<Long>() {
                               @Override
                               public Long reduce(Long value, Tuple tuple) {
                                   return tuple.getLongByField("significance") + value;
                               }
                           }, new Long(0) )
                    .filter( new Filter(){
                        @Override
                        public Boolean predicate(Tuple t) {
                            if( t.getLongByField("significance") >= 1 ){
                                return true;
                            } else {
                                return false;
                            }
                        }},
                        new Fields( "user", "tweet_id", "significance" ));

        }catch(Exception e ){}

        return null;
    }
}
