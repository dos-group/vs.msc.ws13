package de.tu_berlin.citlab.testsuite.mocks;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import de.tu_berlin.citlab.storm.bolts.UDFBolt;
import de.tu_berlin.citlab.storm.helpers.KeyConfigFactory;
import de.tu_berlin.citlab.storm.udf.IOperator;
import de.tu_berlin.citlab.storm.window.CountWindow;
import de.tu_berlin.citlab.storm.window.IKeyConfig;
import de.tu_berlin.citlab.storm.window.Window;
import de.tu_berlin.citlab.testsuite.helpers.DebugLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import java.util.List;
import java.util.Map;


public class UDFBoltMock extends UDFBolt
{
/* Global Private Constants: */
/* ========================= */

    private static final Logger LOGGER = LogManager.getLogger(DebugLogger.UDFBOLTMOCK_ID);
    private static final Marker DETAILED = DebugLogger.getDetailedMarker();

	private static final long serialVersionUID = 1L;


    public final OutputCollector getOutputCollectorMock() { return this.collector; };


/* Constructors: */
/* ============= */
	
	public UDFBoltMock(Fields outputFields, IOperator operator) 
	{
		this(outputFields, operator, new CountWindow<Tuple>(1));
	}

	public UDFBoltMock(Fields outputFields, IOperator operator,
			Window<Tuple, List<Tuple>> window) 
	{
		this(outputFields, operator, window, KeyConfigFactory.DefaultKey());
	}

	public UDFBoltMock(Fields outputFields, IOperator operator,
			Window<Tuple, List<Tuple>> window, IKeyConfig windowKey)
	{
		super(outputFields, operator, window, windowKey, KeyConfigFactory.DefaultKey());
        this.collector = OutputCollectorMock.mockOutputCollector();
	}

    public UDFBoltMock(Fields outputFields, IOperator operator,
                       Window<Tuple, List<Tuple>> window, IKeyConfig windowKey, IKeyConfig groupByKey)
    {
        super(outputFields, operator, window, windowKey, groupByKey);
        this.collector = OutputCollectorMock.mockOutputCollector();
    }
	
	
/* Public Methods: */
/* =============== */

    @Override
    public void execute(Tuple input) {
        super.execute(input);

        LOGGER.debug(DETAILED, "Executed Tuple {}", input.toString());
    }

    @Override
    public void executeBatches(List<List<Tuple>> windows) {
        super.executeBatches(windows);

        //TODO: write List<List<Tuple>> as an LogPrinter String and log it here.
        LOGGER.debug(DETAILED, "Executed Batch of Tuple-Windows.");
    }
}
