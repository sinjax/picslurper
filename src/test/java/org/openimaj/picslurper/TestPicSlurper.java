package org.openimaj.picslurper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;
import org.openimaj.io.FileUtils;
import org.openimaj.io.IOUtils;

public class TestPicSlurper {
	
	@Test
	public void testURLDir() throws Exception {
		File testOut = File.createTempFile("dir", "out");
		testOut.delete();
		testOut.mkdirs();
		testOut.deleteOnExit();
		
		PicSlurper slurper = new PicSlurper();
		File out = StatusConsumer.urlToOutput(new URL("http://www.google.com"),testOut);
		System.out.println(out);
		out = StatusConsumer.urlToOutput(new URL("http://www.google.com/?bees"),testOut);
		System.out.println(out);
		out = StatusConsumer.urlToOutput(new URL("http://www.google.com/some/long/path.html?bees"),testOut);
		System.out.println(out);
	}
	
	@Test
	public void testImageTweets() throws Exception {
		File testIn = File.createTempFile("image", ".txt");
		File testOut = File.createTempFile("image", "out");
		System.out.println("output location: " + testOut);
		testIn.delete();
		testOut.delete();
		FileUtils.copyStreamToFile(TestPicSlurper.class.getResourceAsStream("/images-10.txt"), testIn);
		
		PicSlurper.main(new String[]{"-i",testIn.getAbsolutePath(),"-o",testOut.getAbsolutePath()});
	}
	
	@Test
	public void testURLStream() throws MalformedURLException, IOException{
		InputStream stream = StatusConsumer.urlAsStream(new URL("http://t.co/Hbp0Ff6D")).getInputStream();
		System.out.println(FileUtils.readall(stream));
		stream = StatusConsumer.urlAsStream(new URL("http://p.twimg.com/AwbLNdpCQAEgj5J.jpg")).getInputStream();
	}
}
