package de.tu_berlin.citlab.storm.topologies;


import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.generated.StormTopology;
import backtype.storm.task.OutputCollector;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import de.tu_berlin.citlab.db.CassandraConfig;
import de.tu_berlin.citlab.db.CassandraDAO;
import de.tu_berlin.citlab.db.PrimaryKey;
import de.tu_berlin.citlab.storm.bolts.UDFBolt;
import de.tu_berlin.citlab.storm.helpers.KeyConfigFactory;
import de.tu_berlin.citlab.storm.helpers.TupleHelper;
import de.tu_berlin.citlab.storm.operators.*;
import de.tu_berlin.citlab.storm.operators.join.StaticHashJoinOperator;
import de.tu_berlin.citlab.storm.operators.join.TupleProjection;
import de.tu_berlin.citlab.storm.spouts.TwitterSpout;
import de.tu_berlin.citlab.storm.spouts.UDFSpout;
import de.tu_berlin.citlab.storm.udf.IOperator;
import de.tu_berlin.citlab.storm.window.*;
import de.tu_berlin.citlab.twitter.InvalidTwitterConfigurationException;
import de.tu_berlin.citlab.twitter.TwitterConfiguration;
import de.tu_berlin.citlab.twitter.TwitterUserLoader;

import java.io.Serializable;
import java.util.*;


public class AnalyzeTweetsTopology implements TopologyCreation
{
    private static final int windowSize = 1;
    private static final int slidingOffset = 1;

    //public Window<Tuple, List<Tuple>> WINDOW = new CountWindow<Tuple>(windowSize, slidingOffset);

    public Window<Tuple, List<Tuple>> WINDOW = new TimeWindow<Tuple>(1,1);

    public BaseRichSpout createTwitterSpout() throws InvalidTwitterConfigurationException {
        // Setup up Twitter configuration
        Properties user = TwitterUserLoader.loadUser("twitter.config");
        String[] keywords = new String[] {"der", "die","das","wir","ihr","sie", "dein", "mein", "facebook", "google", "twitter" };
        String[] languages = new String[] {"de"};
        String[] outputFields = new String[] {"user", "tweet_id", "tweet"};
        TwitterConfiguration config = new TwitterConfiguration(user, keywords, languages, outputFields);
        return new TwitterSpout(config);
    }

    public CassandraConfig getCassandraConfig(){
        CassandraConfig cassandraCfg = new CassandraConfig();
        cassandraCfg.setIP("127.0.0.1");
        //cassandraCfg.setIP(CassandraConfig.getCassandraClusterIPFromClusterManager());

        return cassandraCfg;
    }

    public UDFBolt createCassandraTweetsSink(){
        CassandraConfig cassandraCfg = getCassandraConfig();
        cassandraCfg.setParams(  //optional, but defaults not always sensable
                "citstorm",
                "tweets",
                new PrimaryKey("user", "tweet_id"), /* CassandraFactory.PrimaryKey(..)  */
                new Fields(), /*save all fields ->  CassandraFactory.SAVE_ALL_FIELD  */
                false // no counter
        );

        return new UDFBolt(
                new Fields( "user", "tweet_id", "tweet" ),
                new CassandraOperator( cassandraCfg  ),
                WINDOW
        );
    }

    public UDFBolt createCassandraUserSignificanceSink(){
        CassandraConfig cassandraCfg = getCassandraConfig();
        cassandraCfg.setParams(  //optional, but defaults not always sensable
                "citstorm",
                "user_significance",
                new PrimaryKey("user"), /* CassandraFactory.PrimaryKey(..)  */
                new Fields( "significance" ), /*save all fields ->  CassandraFactory.SAVE_ALL_FIELD  */
                true // enable counter-mode
        );

        return new UDFBolt(
                new Fields( "user", "user_significance"),
                new CassandraOperator(cassandraCfg),
                WINDOW
        );
    }


    public UDFBolt flatMapTweetWords(){
        return new UDFBolt(
                new Fields( "user", "tweet_id", "word" ),
                new IOperator(){
                    @Override
                    public void execute(List<Tuple> tuples, OutputCollector collector) {

                        for( Tuple p : tuples ){
                            String[] words = p.getStringByField("tweet").replaceAll("[^a-zA-Z0-9 ]", "").split(" ");
                            for( String word : words ){
                                collector.emit(new Values(p.getValueByField("user"),p.getValueByField("tweet_id"), word.trim().toLowerCase() ));
                            }//for
                        }//for
                    }// execute()
                },
                WINDOW
                );
    }

    public UDFBolt createStaticHashJoin(){

        IKeyConfig groupKey = new IKeyConfig(){
            public Serializable getKeyOf( Tuple tuple) {
                Serializable key = tuple.getSourceComponent();
                return key;
            }
        };


        TupleProjection projection = new TupleProjection(){
            public Values project(Tuple inMemTuple, Tuple tuple) {
                return new Values(
                        tuple.getValueByField("user"),
                        tuple.getValueByField("tweet_id"),
                        tuple.getValueByField("word"),

                        inMemTuple.getValueByField("significance")
                );
            }
        };

        ConspicuousUserDatabase.SIGNIFICANCE_THRESHOLD = 1;

        List<Tuple> badWordJoinSide = new ArrayList<Tuple>();

        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("google", new Long(1) )) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("microsoft", new Long(1) )) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("facebook", new Long(1) )) );

        /*badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("bombe", 100)) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("nuklear", 500)) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("anschlag", 1000)) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("berlin", 10)) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("macht", 100)) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("religion", 200)) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("gott", 50)) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("allah", 1000)) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("heilig", 500)) );

        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("der", 100)) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("die", 100)) );
        badWordJoinSide.add( TupleHelper.createStaticTuple(new Fields("word", "significance"), new Values("das", 100)) );
        */

        return new UDFBolt(
                new Fields( "user", "tweet_id", "word", "significance" ),
                new StaticHashJoinOperator(
                        KeyConfigFactory.compareByFields( new Fields("word")),
                        projection,
                        badWordJoinSide.iterator() ),
                WINDOW
        );
    }

    public UDFBolt reduceUserSignificance(){
        return new UDFBolt(
                new Fields( "user", "tweet_id", "significance" ),
                new IOperator(){
                    @Override
                    public void execute(List<Tuple> tuples, OutputCollector collector) {
                        String user=tuples.get(0).getStringByField("user");
                        Long tweet_id=tuples.get(0).getLongByField("tweet_id");
                        Long total_significance = new Long(0);

                        for( Tuple p : tuples ){
                            Long significance = p.getLongByField("significance");
                            total_significance+=significance;
                        }//for

                        getUDFBolt().log_info("operator", user+" "+tweet_id+" "+total_significance);

                        collector.emit(new Values(user,tweet_id,total_significance ));
                    }// execute()
                },
                WINDOW, //new TimeWindow<Tuple>(1, 1),
                KeyConfigFactory.ByFields("user" )
        );
    }

    public UDFBolt delayTuplesBolt(int sec, int slide, Fields fields){
        return new UDFBolt(
                fields,
                new IOperator(){
                    @Override
                    public void execute(List<Tuple> tuples, OutputCollector collector) {
                        for( Tuple p : tuples ){
                            collector.emit(p.getValues());
                        }//for
                    }//execute()
                },
                new TimeWindow<Tuple>(sec, slide)
        );
    }

    public UDFSpout persistentTupleProvider(){
        final Fields fields = new Fields("user", "significance");

        return new UDFSpout(fields){
            private CassandraDAO dao = new CassandraDAO();
            private Iterator<Values> stored_data;
            private boolean ready=false;

            @Override
            public void open(){
                // load data from cassandra
                dao.setConfig(getCassandraConfig());
                dao.init();
                stored_data = dao.source("citstorm", "user_significance", fields ).findAll();
                ready=true;
            }
            @Override
            public void nextTuple(){
                if(ready){
                    if(stored_data.hasNext() ){
                        Values val = stored_data.next();
                        Long sig = (Long)val.get(1);

                        getOutputCollector().emit(new Values(val.get(0), sig) );
                    } else {
                        //this.close();
                    }
                }
            }
            @Override
            public void close(){
                //stored_data.clear();
            }
        };
    }

    public UDFBolt filterBadUsers(){

        final TupleProjection projection = new TupleProjection(){
            public Values project(Tuple inMemTuple, Tuple tuple) {
                return (Values)tuple.getValues();
            }
        };

        final TupleComparator tupleComparator = KeyConfigFactory.compareByFields(new Fields("user"));

        final Map<Serializable, List<Tuple>> badUsersHT = new HashMap<Serializable, List<Tuple> >();

        final StaticHashJoinOperator staticHashJoinBadUsers =
            new StaticHashJoinOperator(
                    tupleComparator,
                    projection,
                    badUsersHT );


        return new UDFBolt(
                new Fields( "user", "tweet_id", "tweet" ), // output
                new MultipleOperators(
                    // process new bad users
                    new OperatorProcessingDescription(
                            new IOperator(){
                                @Override
                                public void execute(List<Tuple> tuples, OutputCollector collector) {
                                    for( Tuple t : tuples ){
                                        getUDFBolt().log_info("operator", "add new user: " + t);
                                        String user = t.getStringByField("user");
                                        Long currSig = t.getLongByField("significance");

                                        // user exists?
                                        if( badUsersHT.containsKey(tupleComparator.getTupleKey(t)) ){
                                            List<Tuple> keyTuples =  badUsersHT.get(tupleComparator.getTupleKey(t));

                                            // lets assume we have one tuple for each detected user
                                            Long lastSig = keyTuples.get(0).getLongByField("significance");
                                            Long totalSig = lastSig+currSig;

                                            Tuple newUserTuple = TupleHelper.createStaticTuple(new Fields("user", "significance"), new Values(user, totalSig) );
                                            keyTuples.clear();
                                            keyTuples.add(newUserTuple);

                                            // do not output any tuples
                                            getUDFBolt().log_info("operator", "update user: " + t + ", sig: " + totalSig);

                                        } else {
                                            Long totalSig = currSig;
                                            Tuple newUserTuple = TupleHelper.createStaticTuple(new Fields("user", "significance"), new Values(user, totalSig) );

                                            List<Tuple> badUsers = new ArrayList<Tuple>();
                                            badUsers.add(newUserTuple);

                                            badUsersHT.put(tupleComparator.getTupleKey(t), badUsers );

                                            System.out.println("add new user");

                                            getUDFBolt().log_info("operator","add new user: "+t+", sig: "+totalSig);
                                        }
                                    }
                                }// execute()
                            },
                            "reduce_to_user_significance", "persistent_tuple_provider"
                    ),
                    // process raw comping tweets
                    new OperatorProcessingDescription(
                            staticHashJoinBadUsers,
                            "tweets"
                        )
                ),
            WINDOW,
            KeyConfigFactory.BySource()
        );
    }



    @Override
    public StormTopology createTopology()
    {
        TopologyBuilder builder = new TopologyBuilder();

        // provide twitter streaming data
        try {
            builder.setSpout("tweets", createTwitterSpout(), 1);

        } catch (InvalidTwitterConfigurationException e) {
            e.printStackTrace();
        }

        // find bad users
        builder.setSpout("persistent_tuple_provider", persistentTupleProvider(), 1);

        // find bad users
        builder.setBolt("flatmap_tweet_words", flatMapTweetWords(), 1)
                .shuffleGrouping("tweets");

        /*builder.setBolt("delayed_tweets", delayTuplesBolt(5, 5, new Fields("user", "tweet_id", "tweet") ), 1 )
                .shuffleGrouping("tweets");*/

        Fields fieldsGroupByUser = new Fields("user");

        // filter and find bad users
        builder.setBolt("filter_bad_users", filterBadUsers(), 1)
                .fieldsGrouping("reduce_to_user_significance", fieldsGroupByUser)
                .fieldsGrouping("tweets", fieldsGroupByUser)
                .fieldsGrouping("persistent_tuple_provider", fieldsGroupByUser );

        builder.setBolt("store_tweets", createCassandraTweetsSink(), 1)
                .shuffleGrouping("filter_bad_users");


        builder.setBolt("join_with_badwords", createStaticHashJoin(), 1)
                .shuffleGrouping("flatmap_tweet_words");

        builder.setBolt("reduce_to_user_significance", reduceUserSignificance(), 1)
                .fieldsGrouping("join_with_badwords", fieldsGroupByUser);


        builder.setBolt("store_user_significance", createCassandraUserSignificanceSink(), 1)
                .shuffleGrouping("reduce_to_user_significance");


        return builder.createTopology();
    }


    @SuppressWarnings("serial")
    public static void main(String[] args) throws Exception {

        Config conf = new Config();
        conf.setDebug(true);

        conf.setMaxTaskParallelism(1);
        conf.setMaxSpoutPending(1);

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology("analyzte-twitter-stream", conf,
            new AnalyzeTweetsTopology().createTopology() );
    }

}
