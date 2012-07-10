package org.openimaj.picslurper;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.openimaj.twitter.collection.StreamJSONStatusList;
import org.openimaj.twitter.collection.StreamJSONStatusList.ReadableWritableJSON;

import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.IRichSpout;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;

public class InputStreamSpout implements IRichSpout{

	private SpoutOutputCollector collector;
	private Iterator<ReadableWritableJSON> tweets;

	public InputStreamSpout(InputStream stream) throws IOException {
		this.tweets = StreamJSONStatusList.read(stream, "UTF-8").iterator();
	}

	@Override
	public void open(Map conf, TopologyContext context,SpoutOutputCollector collector) {
		this.collector = collector;
	}

	@Override
	public void close() {
		// Do nothing?
	}

	@Override
	public void activate() {
		
	}

	@Override
	public void deactivate() {
	}

	@Override
	public void nextTuple() {
		if(!tweets.hasNext()){
			this.notifyAll();
			return;
		}
		ReadableWritableJSON t = tweets.next();
		StringWriter writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		String out = "";
		try {
			t.writeASCII(printWriter);
			printWriter.flush();
			out = writer.toString();
		} catch (IOException e) {
		}
		collector.emit(Arrays.asList(new Object[]{out}));
	}

	@Override
	public void ack(Object msgId) {
		
	}

	@Override
	public void fail(Object msgId) {
		
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("tweet"));
	}

	@Override
	public Map<String, Object> getComponentConfiguration() {
		return null;
	}

}
