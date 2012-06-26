package org.openimaj.picslurper;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
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
		cons.nTweets=1;
		cons.nURLs=0;
		
		Matcher matcher = urlPattern.matcher(status.text);
		while(matcher.find()){
			cons.nURLs++;
			String urlString = status.text.substring(matcher.start(),matcher.end());
			File urlOut = resolveURL(new URL(urlString));
			if(urlOut!=null){
				cons.nImages++;
			}
		}
		if(this.slurper.stats) PicSlurper.updateStats(this.slurper.globalStatus,cons);
		return cons;
	}
	
	File resolveURL(URL url) {
		
		MBFImage image = null;
		try {
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
	        conn.setConnectTimeout(15000);
	        conn.setReadTimeout(15000);
	        conn.setInstanceFollowRedirects(true);
	        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.0; ru; rv:1.9.0.11) Gecko/2009060215 Firefox/3.0.11 (.NET CLR 3.5.30729)");
	        conn.connect();
			image = ImageUtilities.readMBF(conn.getInputStream());
		} catch (Throwable e) { // This input might not be an image! deal with that
			System.out.println("Resolving url: " + url + " FAILED");
			return null; 
		}
		File outputDir;
		try {
			outputDir = urlToOutput(url,slurper.outputLocation);
			File outImage = new File(outputDir,"image.png");
			File outStats = new File(outputDir,"status.txt");
			StatusConsumption cons = new StatusConsumption();
			cons.nTweets++;
			PicSlurper.updateStats(outStats,cons);
			ImageUtilities.write(image, outImage);
			System.out.println("Resolving url: " + url + " SUCCESS");
			return outputDir;
		} catch (IOException e) {
		}
		System.out.println("Resolving url: " + url + " FAILED");
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
			if(outFile.isDirectory()) {
				return outFile;
			}
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
