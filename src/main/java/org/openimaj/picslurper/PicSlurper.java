package org.openimaj.picslurper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.openimaj.image.ImageUtilities;
import org.openimaj.image.MBFImage;
import org.openimaj.io.IOUtils;
import org.openimaj.text.nlp.TweetTokeniser;
import org.openimaj.text.nlp.TweetTokeniserException;
import org.openimaj.text.nlp.patterns.URLPatternProvider;
import org.openimaj.tools.FileToolsUtil;
import org.openimaj.tools.InOutToolOptions;
import org.openimaj.twitter.TwitterStatus;
import org.openimaj.twitter.collection.StreamTwitterStatusList;
import org.openimaj.twitter.collection.TwitterStatusList;

public class PicSlurper extends InOutToolOptions implements Iterable<InputStream>, Iterator<InputStream>{
	
	String[] args;
	boolean stdin;
	List<File> inputFiles;
	boolean stdout;
	File outputLocation;
	File globalStatus;
	Iterator<File> fileIterator;
	File inputFile;
	private static final String STATUS_FILE_NAME = "status.txt";

	@Option(name="--encoding", aliases="-e", required=false, usage="The outputstreamwriter's text encoding", metaVar="STRING")
	String encoding = "UTF-8";
	
	@Option(name="--no-stats", aliases="-ns", required=false, usage="Don't try to keep stats of the tweets seen", metaVar="STRING")
	boolean stats = true;
	
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
				this.outputLocation = FileToolsUtil.validateLocalOutput(this);
				this.outputLocation.mkdirs();
				this.globalStatus = new File(outputLocation,STATUS_FILE_NAME);
				IOUtils.writeASCII(this.globalStatus, new StatusConsumption()); // initialise the output file
			}
		}
		catch(Exception e){
			throw new CmdLineException(null,e.getMessage());
		}
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
	
	void start() throws IOException, TweetTokeniserException, InterruptedException {
		ExecutorService service = Executors.newFixedThreadPool(nThreads);
		for (InputStream inStream : this) {
			TwitterStatusList<TwitterStatus> tweets = StreamTwitterStatusList.read(inStream, this.encoding);
			for (TwitterStatus status : tweets) {
				service.submit(consumeStatus(status));
			}
		}
		service.shutdown();
		service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
	}
	
	StatusConsumer consumeStatus(TwitterStatus status) throws IOException {
		return new StatusConsumer(status,this);
	}

	@Override
	public Iterator<InputStream> iterator() {
		return this;
	}
	public static void main(String[] args) throws IOException, TweetTokeniserException, InterruptedException {
		PicSlurper slurper = new PicSlurper(args);
		slurper.prepare();
		slurper.start();
	}

	

	

}
