package de.tu_berlin.citlab.storm.topologies;

import backtype.storm.generated.StormTopology;
import backtype.storm.task.OutputCollector;
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
import de.tu_berlin.citlab.storm.operators.*;
import de.tu_berlin.citlab.storm.operators.join.StaticHashJoinOperator;
import de.tu_berlin.citlab.storm.operators.join.TupleProjection;
import de.tu_berlin.citlab.storm.sinks.CassandraSink;
import de.tu_berlin.citlab.storm.spouts.CassandraDataProviderSpout;
import de.tu_berlin.citlab.storm.spouts.UDFSpout;
import de.tu_berlin.citlab.storm.udf.IOperator;
import de.tu_berlin.citlab.storm.window.TimeWindow;
import de.tu_berlin.citlab.storm.window.TupleComparator;
import de.tu_berlin.citlab.storm.window.Window;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AnalyzeTweetsTopologyWithStreamBuilder implements TopologyCreation {

    @Override
    public StormTopology createTopology() {
        String cassandraServerIP = "127.0.0.1";

        StreamBuilder stream = new StreamBuilder();

        try{

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


            // HashTable
            final Map<Serializable, List<Tuple>> badUsersHashTable = new HashMap<Serializable, List<Tuple> >();

            String[] keywords = new String[] {"der", "die","das","wir","ihr","sie", "dein", "mein", "facebook", "google", "twitter" };
            String[] languages = new String[] {"de"};
            String[] tweets_outputfields = new String[] {"user", "tweet_id", "tweet"};

            stream.setDefaultWindowType(new TimeWindow<Tuple>(1,1));

            StreamSource tweets = new TwitterStreamSource(stream, keywords, languages, tweets_outputfields);

            SinkOperator tweetsSink = new CassandraOperator(cassandraTweetsCfg);
            SinkOperator userSignificanceSink = new CassandraOperator(cassandraUserSignificanceCfg);
            SinkOperator badWordsStatisticsSink = new CassandraOperator(cassandraBadWordsStatisticsCfg);

            // delay tweets for 5 seconds, to make sure that the tweets are analyzed
            StreamNode delayedTweets = tweets.delay(5);

            StreamNode badWords =
                tweets.flapMap(new FlatMapper() {
                        @Override
                        public List<List<Object>> flatMap(Tuple tuple) {
                            String[] words = tuple.getStringByField("tweet")
                                    .replaceAll("[^\\p{L}\\p{Nd} ]", "").trim().split(" +");
                            List<List<Object>> result = new ArrayList<>();
                            for (String word : words) {
                                System.out.println("extract word: "+word+"("+word.length()+") -> "+tuple.getStringByField("tweet"));
                                result.add(new Values(tuple.getValueByField("user"),
                                        tuple.getValueByField("tweet_id"),
                                        word.trim().toLowerCase()));
                            }
                            return result;
                        }
                    },
                    new Fields("user", "tweet_id", "word"));

            StreamNode joinedBadWords =
                badWords.join( badWordJoinSide.iterator(),
                                TupleProjection.project( new Fields("user", "tweet_id", "word"), new Fields("significance")),
                                KeyConfigFactory.compareByFields(new Fields("word")),
                                new Fields("user", "tweet_id", "word", "significance"));

            // prepare data to save in cassandra
            // store join words
            joinedBadWords
                    .map(new Mapper() {
                             @Override
                             public List<Object> map(Tuple tuple) {
                                 return new Values(tuple.getValueByField("word"), new Long(1));
                             }
                         },
                            new Fields("word", "count")
                    )
                    .save(badWordsStatisticsSink);



            StreamNode detectedUsers =
                joinedBadWords
                    .reduce(new Fields("user", "tweet_id"),
                           new Reducer<Long>() {
                               @Override
                               public Long reduce(Long value, Tuple tuple) {
                                   return tuple.getLongByField("significance") + value;
                               }
                           }, new Long(0),
                           new Fields( "user", "tweet_id", "significance" ))
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


            final TupleComparator compareUser = KeyConfigFactory.compareByFields(new Fields("user"));

            delayedTweets.case_merge( new Fields(tweets_outputfields) )
                         .source(detectedUsers /*, "persistent_tuple_provider"*/)
                         .join( badUsersHashTable,
                                TupleProjection.projectLeft(),
                                KeyConfigFactory.compareByFields(new Fields("user")) )

                         .source()
                         .execute( new IOperator(){
                                           @Override
                                           public void execute(List<Tuple> tuples, OutputCollector collector) {
                                               for( Tuple t : tuples ){
                                                   getUDFBolt().log_info("operator", "add new user: " + t);
                                                   String user = t.getStringByField("user");
                                                   Long currSig = t.getLongByField("significance");

                                                   // user exists?
                                                   if( badUsersHashTable.containsKey(compareUser.getTupleKey(t)) ){
                                                       List<Tuple> keyTuples =  badUsersHashTable.get(compareUser.getTupleKey(t));

                                                       // lets assume we have one tuple for each detected user
                                                       Long lastSig = keyTuples.get(0).getLongByField("significance");
                                                       Long totalSig = lastSig+currSig;

                                                       Tuple newUserTuple = TupleHelper.createStaticTuple(new Fields("user", "significance"), new Values(user, totalSig) );
                                                       keyTuples.clear();
                                                       keyTuples.add(newUserTuple);

                                                       // do not output any tuples
                                                       getUDFBolt().log_info("operator", "update user: " + t + ", sig: " + totalSig);

                                                       getUDFBolt().log_statistics("update user sig: " + totalSig + " " + newUserTuple);

                                                   } else {
                                                       Long totalSig = currSig;
                                                       Tuple newUserTuple = TupleHelper.createStaticTuple(new Fields("user", "significance"), new Values(user, totalSig) );

                                                       List<Tuple> badUsers = new ArrayList<Tuple>();
                                                       badUsers.add(newUserTuple);

                                                       badUsersHashTable.put(compareUser.getTupleKey(t), badUsers );

                                                       getUDFBolt().log_statistics("add new user sig: " + totalSig + " " + newUserTuple);

                                                       getUDFBolt().log_info("operator","add new user: "+t+", sig: "+totalSig);
                                                   }
                                               }
                                           }// execute()
                                       } );


        }catch(Exception e ){
            e.printStackTrace();
        }

        return stream.getTopologyBuilder().createTopology();
    }
}