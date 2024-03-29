package de.tu_berlin.citlab.db;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import org.apache.commons.lang.StringUtils;
import backtype.storm.tuple.Tuple;
import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;

public class CassandraDAO implements DAO, Serializable
{
	private Session session;
	private Cluster cluster;
	private PreparedStatement preparedStatement;
	public BoundStatement boundStatement;
	public BatchStatement batchStatement;
	public CassandraConfig config = null;
	public String createKeyspaceQuery;
	public String createTableQueryByFields;
    public String[] selectFields;
	public TupleAnalyzer ta;
	private Select select;
	private Map <String, String> table_inf = new HashMap <String, String>();

	public CassandraDAO()
	{

	}

	public void connect( String node )
	{
		cluster = Cluster.builder().addContactPoint( node ).build();
		Metadata metadata = cluster.getMetadata();
		System.out.printf( "Connected to cluster: %s\n", metadata.getClusterName() );
		for ( Host host : metadata.getAllHosts() )
		{
			System.out.printf( "Datacenter: %s; Host: %s; Rack: %s\n", host.getDatacenter(),
					host.getAddress(), host.getRack() );
		}
		session = cluster.connect();
	}
	
	public void shutdown()
	{
		session.shutdown();
	}

	public void createKeyspace( String query )
	{
		session.execute( query );
	}

	public void createTable( String query )
	{
		session.execute( query );
	}

	public void init()
	{
		if (config != null)
		{
			connect( config.getIP() );
		}
		else
		{
            System.err.println("Could not connect to cassandra server, invalid config ");
		}
	}

    //TODO: do we need this function any more?
	public DBConfig createConfig()
	{
		this.config = new CassandraConfig();
		return config;
	}

    public void setConfig( CassandraConfig config ){
        this.config=config;
    }
	
	public void analyzeTuple( Tuple tuple )
	{
		ta = new TupleAnalyzer( tuple );
		ta.setPrimaryKey( config.getPrimaryKeys() );  //TODO: pk anderswo behandeln
		createKeyspaceQuery = ta.createKeyspaceQuery( config.getKeyspace() );
		createTableQueryByFields = ta.createTableQueryByFields( config.getKeyspace(), config.getTable(), ta.fieldsInTuple.toList() );
    }

    public void prepare(){
        setPreparedStatement(ta.createPreparedInsertStatement(config.getKeyspace(), config.getTable()));
    }
	
	public void createDataStructures()
	{
		session.execute( createKeyspaceQuery );  //TODO: exeption handling
		session.execute( createTableQueryByFields );
	}

	public byte[] serializeObject( Object obj )
	{
		byte[] byteArray = {};
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try 
		{ 
			ObjectOutputStream out = new ObjectOutputStream(bos);
			out.writeObject( obj );
			out.close();
			byteArray = bos.toByteArray();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		return byteArray;
	}
	
	public void store( List <Tuple> tuples )
	{
        makeBatch();

        for ( Tuple tuple: tuples )
		{
			List <Object> values = new ArrayList <Object>();
			for ( int i = 0; i < ta.cassandraTypesInTuple.size(); i++ )
			{
				String s = ta.javaTypesInTuple.get(i);
				if (s.equals("String")) {
					values.add(tuple.getString(i));

				} else if (s.equals("Integer")) {
					values.add(tuple.getInteger(i));

				} else if (s.equals("Long")) {
					values.add(tuple.getLong(i));

				} else if (s.equals("blob")) {
					values.add(ByteBuffer.wrap(serializeObject(tuple.getValue(i))));

				}
			}
			bindValues( values.toArray( new Object[ values.size() ] ) );
			addToBatch( boundStatement );
		}

		batchExecute();
	}

	public void makeBatch()
	{
		this.batchStatement = new BatchStatement();
	}

	public BoundStatement getBoundStatement()
	{
		return boundStatement;
	}

	public void addToBatch( BoundStatement boundStatement )
	{
		batchStatement.add( boundStatement );
	}

	public void batchExecute()
	{
		session.execute(batchStatement);
	}

	public void bindValues( Object... objects )
	{
		assert this.preparedStatement != null: "Prepared Statement darf beim Binden nicht Null sein!";
		boundStatement = new BoundStatement( preparedStatement ).bind( objects );
	}

	public void setPreparedStatement( String query )
	{
		preparedStatement = this.session.prepare( query );
	}
	

	public CassandraDAO source(String keyspace, String table, Fields fields )
	{
		this.table_inf.put( "keyspace", keyspace );
		this.table_inf.put( "table", table );
        selectFields = new String[fields.size()];
        fields.toList().toArray(selectFields );
		return this;
	}
	
	public Iterator <Values> findAll()
	{
		Statement st = QueryBuilder.select(selectFields).from( table_inf.get( "keyspace" ), table_inf.get( "table" ) );
		return getValuesFromStatement( st ).iterator();
	}
	

	public Iterator <Values> findBy( String rowkey, String rowkey_value )
	{
		Statement st = QueryBuilder.select(selectFields).from( table_inf.get( "keyspace" ), table_inf.get( "table" ) )
				.where( QueryBuilder.eq( rowkey, rowkey_value  ) );
				
		return getValuesFromStatement( st ).iterator();
		
	}
	
	private List <Values> getValuesFromStatement( Statement st )
	{
		List <Values> listOfValues = new ArrayList <Values>();

		ResultSet rs = this.session.execute( st );
		List<Row> all = new ArrayList<Row>();
		all = rs.all();
		for ( int i = 0; i < all.size(); i++ )
		{
			Values values = new Values();
			
			Row row = all.get( i );

			ColumnDefinitions cd = row.getColumnDefinitions();

			int j = 0;
			for (ColumnDefinitions.Definition def : cd)
			{
				if ( def.getType().getName().toString().equals( "text" ) )
				{
					values.add( row.getString( j ) );
				}
				else if ( def.getType().getName().toString().equals( "int" ) )
				{
					values.add( row.getInt( j ) );
				}
				else if ( def.getType().getName().toString().equals( "bigint" ) )
				{
					values.add( row.getLong( j ) );
				}
				else if ( def.getType().getName().toString().equals( "blob" ) )
				{
					values.add( row.getBytes( j ).array() );
				}
				else if ( def.getType().getName().toString().equals( "varchar" ) )
				{
					values.add( row.getString( j ) );
				}
                else if ( def.getType().getName().toString().equals( "counter" ) )
                {
                    values.add( row.getLong( j ) );
                }
				j++;
			}
			listOfValues.add( values );
		}
		return listOfValues;
	}


}



