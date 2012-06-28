package org.openimaj.picslurper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.mapred.join.StreamBackedIterator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.openimaj.io.FileUtils;
import org.openimaj.io.IOUtils;
import org.openimaj.text.nlp.TweetTokeniserException;
import org.openimaj.tools.FileToolsUtil;
import org.openimaj.tools.InOutToolOptions;
import org.openimaj.twitter.GeneralJSONTwitter;
import org.openimaj.twitter.USMFStatus;
import org.openimaj.twitter.collection.StreamJSONStatusList;
import org.openimaj.twitter.collection.StreamJSONStatusList.ReadableWritableJSON;
import org.openimaj.twitter.collection.StreamTwitterStatusList;
import org.openimaj.twitter.collection.TwitterStatusList;
import org.openimaj.util.list.AbstractStreamBackedList;

public class PicSlurper extends InOutToolOptions implements Iterable<InputStream>, Iterator<InputStream>{
	
	private static String TWEET_FILE_NAME = "tweets.json";
	String[] args;
	boolean stdin;
	List<File> inputFiles;
	boolean stdout;
	File outputLocation;
	File globalStatus;
	Iterator<File> fileIterator;
	File inputFile;
	private static final String STATUS_FILE_NAME = "status.txt";
	private static final int MAX_QUEUED_JOBS = 1000;

	@Option(name="--encoding", aliases="-e", required=false, usage="The outputstreamwriter's text encoding", metaVar="STRING")
	String encoding = "UTF-8";
	
	@Option(name="--no-stats", aliases="-ns", required=false, usage="Don't try to keep stats of the tweets seen", metaVar="STRING")
	boolean stats = true;
	
	@Option(name="--no-continue", aliases="-nc", required=false, usage="Do not continue an existing output", metaVar="STRING")
	boolean contin = true;
	
	@Option(name="--no-threads", aliases="-j", required=false, usage="Threads used to download images, defaults to n CPUs", metaVar="STRING")
	int nThreads = Runtime.getRuntime().availableProcessors();
	

	public PicSlurper(String[] args) {
		this.args = args;
	}

	public PicSlurper() {
		this.args = new String[]{};
	}

	/**
	 * prepare the tool for running
	 */
	public void prepare(){
		CmdLineParser parser = new CmdLineParser(this);
		try {
			parser.parseArgument(args);
			this.validate();
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("Usage: java -jar JClusterQuantiser.jar [options...] [files...]");
			parser.printUsage(System.err);
			System.err.println(this.getExtractUsageInfo());
			System.exit(1);
		}
		
	}
	
	String getExtractUsageInfo() {
		return "Grab some images and some stats";
	}
	
	void validate() throws CmdLineException {
		try{
			if(FileToolsUtil.isStdin(this)){
				this.stdin = true;
			}
			else{				
				this.inputFiles = FileToolsUtil.validateLocalInput(this);
				this.fileIterator = this.inputFiles.iterator();
			}
			if(FileToolsUtil.isStdout(this)){
				this.stdout = true;
			}
			else
			{
				this.outputLocation = validateLocalOutput(this.getOutput(),this.isForce(),this.contin);
				this.outputLocation.mkdirs();
				this.globalStatus = new File(outputLocation,STATUS_FILE_NAME);
				updateStats(this.globalStatus, new StatusConsumption()); // initialise the output file
			}
		}
		catch(Exception e){
			throw new CmdLineException(null,e.getMessage());
		}
	}
	
	/**
	 * Validate the (local) ouput from an String and return the 
	 * corresponding file.
	 * 
	 * @param out where the file will go
	 * @param overwrite whether to overwrite existing files
	 * @param contin whether an existing output should be continued (i.e. ignored if it exists)
	 * @return the output file location, deleted if it is allowed to be deleted
	 * @throws IOException if the file exists, but can't be deleted
	 */
	public static File validateLocalOutput(String out, boolean overwrite, boolean contin) throws IOException {
		if(out == null){
			throw new IOException("No output specified");
		}
		File output = new File(out);
		if(output.exists()){
			if(overwrite){
				if(!FileUtils.deleteRecursive(output)) throw new IOException("Couldn't delete existing output");
			}
			else if(!contin){
				throw new IOException("Output already exists, didn't remove");
			}
		}
		return output;
	}
	
	@Override
	public boolean hasNext() {
		if(!this.stdin) {
			if(fileIterator == null) return false;
			return fileIterator.hasNext();
		}
		return true;
	}

	@Override
	public InputStream next() {
		if(this.stdin) return System.in;
		if(fileIterator.hasNext())
		{
			this.inputFile = fileIterator.next();
			try {
				return new FileInputStream(this.inputFile);
			} catch (FileNotFoundException e) {
			}
		}
		else
			this.inputFile = null;
		return null;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
	
	List<Future<?>> futureList = new ArrayList<Future<?>>();
	
	void start() throws IOException, TweetTokeniserException, InterruptedException {
		ExecutorService service = Executors.newFixedThreadPool(nThreads);
		for (InputStream inStream : this) {
			if(countFutures() > MAX_QUEUED_JOBS){
				waitForFutures(service); // is this a good idea? should we skip tweets instead?
			}
			StreamJSONStatusList tweets = StreamJSONStatusList.read(inStream, "UTF-8");
			for (ReadableWritableJSON status : tweets) {
				futureList.add(service.submit(consumeStatus(status)));
			}
		}
		service.shutdown();
		service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
	}
	
	private void waitForFutures(ExecutorService service) {
		while(countFutures() > MAX_QUEUED_JOBS){
			try {
				Thread.sleep(5000l);// 5 seconds
			} catch (InterruptedException e) {
			}
		}
	}

	private int countFutures() {
		for (Iterator iterator = this.futureList.iterator(); iterator.hasNext();) {
			Future<?> type = (Future<?>) iterator.next();
			if(type.isDone()) iterator.remove();
			
		}
		return this.futureList.size();
	}

	StatusConsumer consumeStatus(ReadableWritableJSON status) throws IOException {
		return new StatusConsumer(status,this);
	}

	@Override
	public Iterator<InputStream> iterator() {
		return this;
	}
	
	public static synchronized void updateStats(File statsFile, StatusConsumption statusConsumption) throws IOException {
		StatusConsumption current = new StatusConsumption();
		if(statsFile.exists()) current = IOUtils.read(statsFile,current);
		current.incr(statusConsumption);
		IOUtils.writeASCII(statsFile, current); // initialise the output file
	}
	public static synchronized void updateTweets(File outRoot, ReadableWritableJSON status) throws IOException {
		File outFile = new File(outRoot,TWEET_FILE_NAME);
		FileWriter fstream = new FileWriter(outFile,true);
		PrintWriter pwriter = new PrintWriter(fstream);
		status.writeASCII(pwriter);
		pwriter.println();
		pwriter.flush();
		pwriter.close();
	}
	
	public static void main(String[] args) throws IOException, TweetTokeniserException, InterruptedException {
		PicSlurper slurper = new PicSlurper(args);
		slurper.prepare();
		slurper.start();
	}

	

	

	

}
