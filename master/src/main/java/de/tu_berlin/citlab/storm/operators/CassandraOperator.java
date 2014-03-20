package de.tu_berlin.citlab.storm.operators;

import backtype.storm.task.OutputCollector;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import de.tu_berlin.citlab.db.*;
import de.tu_berlin.citlab.storm.exceptions.OperatorException;
import de.tu_berlin.citlab.storm.udf.IOperator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class CassandraOperator extends IOperator {

    private boolean initialized = false;
    private boolean isCounterBolt = false;

    private CassandraDAO dao = new CassandraDAO();

    private CassandraConfig config;
    private Counter ctn;

    private Fields keyFields;


    public CassandraOperator( CassandraConfig config ){
        this.config = config;
        
        if(this.config.isCounterBolt() ){
            this.ctn = new Counter( config );
        }
    }

    @Override
    public void execute(List<Tuple> tuples, OutputCollector collector) throws OperatorException {
        // First tuple used to initialize datastructures and derive data types
        if ( !initialized )
        {
            if( !config.isCounterBolt() ) {
                dao.setConfig(config);
                dao.init();
                dao.analyzeTuple( tuples.get(0) );
                dao.createDataStructures();

            }
            else {
                keyFields = new Fields(config.getPrimaryKeys().getPrimaryKeyFields());

                ctn.connect( config.getIP() );
                ctn.createDataStructures();

            }

            initialized = true;
        }

        try{
            if( !config.isCounterBolt() ) {

                for( Tuple t : tuples ){
                    this.getUDFBolt().log_debug("operator", "store " + t);
                }

                dao.store( tuples );
            } else {

                for( Tuple t : tuples ){

                    List<Object> keyValues = t.select( keyFields  );
                    List<Object> val = t.select(config.getTupleFields());

                    this.getUDFBolt().log_debug("debug: store " + t);

                    ctn.update( keyValues, (int)val.get(0) );
                }//for
            }


        } catch (Exception e ){
            this.getUDFBolt().log_error("Storing of tuples into Cassandra DB failed!", e );

			throw new OperatorException("Storing of tuples into Cassandra DB failed!");
        }

        // emit tuples
        for( Tuple p : tuples ){
            collector.emit(p.getValues());
        }
    }

}
