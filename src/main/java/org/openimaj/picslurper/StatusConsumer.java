package org.openimaj.picslurper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openimaj.image.ImageUtilities;
import org.openimaj.image.MBFImage;
import org.openimaj.io.IOUtils;
import org.openimaj.text.nlp.patterns.URLPatternProvider;
import org.openimaj.twitter.TwitterStatus;

public class StatusConsumer implements Callable<StatusConsumption>{
	
	final static Pattern urlPattern = new URLPatternProvider().pattern();
	private TwitterStatus status;
	private PicSlurper slurper;

	public StatusConsumer(TwitterStatus status,PicSlurper slurper) {
		this.status = status;
		this.slurper = slurper;
	}
	
	@Override
	public StatusConsumption call() throws Exception {
		StatusConsumption cons = new StatusConsumption();
		cons.nTweets++;
		
		Matcher matcher = urlPattern.matcher(status.text);
		while(matcher.find()){
			cons.nURLs++;
			String urlString = status.text.substring(matcher.start(),matcher.end());
			File urlOut = resolveURL(new URL(urlString));
			if(urlOut!=null){
				cons.nImages++;
			}
		}
		if(this.slurper.stats) updateStats(this.slurper.globalStatus,cons);
		return cons;
	}
	
	private static synchronized void updateStats(File statsFile, StatusConsumption statusConsumption) throws IOException {
		StatusConsumption current = IOUtils.read(statsFile,StatusConsumption.class);
		current.incr(statusConsumption);
		IOUtils.writeASCII(statsFile, current); // initialise the output file
	}
	
	File resolveURL(URL url) {
		System.out.println("Resolving url: " + url);
		MBFImage image = null;
		try {
			image = ImageUtilities.readMBF(url.openConnection().getInputStream());
		} catch (Throwable e) { // This input might not be an image! deal with that
			return null;
		}
		File outputDir;
		try {
			outputDir = urlToOutput(url,slurper.outputLocation);
			File outImage = new File(outputDir,"image.png");
			ImageUtilities.write(image, outImage);
			return outputDir;
		} catch (IOException e) {
		}
		return null;
		
	}
	static synchronized File urlToOutput(URL url, File outputLocation) throws IOException {
		String urlPath = url.getProtocol() + File.separator +
						 url.getHost() + File.separator;
		if(!url.getPath().equals("")) urlPath += url.getPath() + File.separator;
		if(url.getQuery()!= null) urlPath += url.getQuery() + File.separator;
		
		String outPath = outputLocation.getAbsolutePath() + File.separator + urlPath;
		File outFile = new File(outPath);
		if(outFile.exists()){
			if(outFile.isDirectory()) return outFile;
			else{
				createURLOutDir(outFile);
			}
		}else{
			createURLOutDir(outFile);
		}
		return outFile;
	}

	static void createURLOutDir(File outFile) throws IOException {
		if(!((!outFile.exists() || outFile.delete()) && outFile.mkdirs())){
			throw new IOException("Couldn't create URL output: " + outFile.getAbsolutePath());
		}
	}


}
