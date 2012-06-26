package org.openimaj.picslurper;

import java.io.File;
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
		FileUtils.copyStreamToFile(TestPicSlurper.class.getResourceAsStream("/images-100.txt"), testIn);
		
		PicSlurper.main(new String[]{"-i",testIn.getAbsolutePath(),"-o",testOut.getAbsolutePath()});
	}
}
