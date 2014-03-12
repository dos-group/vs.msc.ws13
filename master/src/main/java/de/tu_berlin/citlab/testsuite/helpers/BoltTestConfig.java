package de.tu_berlin.citlab.testsuite.helpers;

import de.tu_berlin.citlab.storm.bolts.UDFBolt;

import java.util.List;

/**
 * Created by Constantin on 12.03.14.
 */
public class BoltTestConfig
{
    public final String testName;
    public final UDFBolt testBolt;
    public final List<List<Object>> assertedOutput;


    public BoltTestConfig(String testName, UDFBolt testBolt,
                          List<List<Object>> assertedOutput)
    {
        this.testName = testName;
        this.testBolt = testBolt;
        this.assertedOutput = assertedOutput;
    }
}
